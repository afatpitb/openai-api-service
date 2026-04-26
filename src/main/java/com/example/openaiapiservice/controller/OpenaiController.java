package com.example.openaiapiservice.controller;

import com.example.openaiapiservice.dto.ChatRequestDTO;
import com.example.openaiapiservice.model.ChatCompletion;
import com.example.openaiapiservice.service.ChatCompletionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration; import java.util.*;


@RestController @RequestMapping("/v1/chat") public class OpenaiController {
    // 简单限流：每秒 5 次
    private static final RateLimiter rateLimiter = RateLimiter.create(5.0);
// 简单异步执行器 private final SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("sse-stream-");

    private final ChatCompletionService chatCompletionService;
    private final ObjectMapper objectMapper;
    public OpenaiController(ChatCompletionService chatCompletionService, ObjectMapper objectMapper) {
        this.chatCompletionService = chatCompletionService;
        this.objectMapper = objectMapper;
    }
    @PostMapping("/completions")
    public Object createCompletion(@RequestBody @Valid ChatRequestDTO chatRequestDTO) throws Exception {
        if (!rateLimiter.tryAcquire()) {
            Map<String, Object> err = Map.of("error", Map.of("message", "Too many requests, please try again later.", "type", "rate_limit_error"));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(err);
        }
        ChatCompletion completion = chatCompletionService.createCompletion(chatRequestDTO);
        boolean stream = Boolean.TRUE.equals(chatRequestDTO.getStream());

        if (!stream) {
            // ✅ 调用 OpenAI
            String responseJson = chatCompletionService.callOpenAI(chatRequestDTO);

            // 保存返回结果
            chatCompletionService.updateResponse(completion.getId(), responseJson);

            // 返回给前端
            return ResponseEntity.ok(objectMapper.readValue(responseJson, Map.class));
        }

        // 流式：直接返回 SseEmitter
        SseEmitter emitter = new SseEmitter(0L); // 不设置超时，或根据需要设置超时

        emitter.onCompletion(() -> {
            // 可记录日志或做资源清理
        });
        emitter.onTimeout(() -> {
            emitter.complete();
        });
        final SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("sse-stream-");

        executor.execute(() -> {
            try {
                // 初始 delta（assistant 角色）
                Map<String, Object> initChunk = new HashMap<>();
                initChunk.put("id", completion.getId());
                initChunk.put("object", "chat.completion.chunk");
                initChunk.put("created", completion.getCreated().getEpochSecond());
                initChunk.put("model", completion.getModel());
                Map<String, Object> deltaInit = Map.of("role", "assistant");
                Map<String, Object> choiceInit = new HashMap<>();
                choiceInit.put("index", 0);
                choiceInit.put("delta", deltaInit);
                choiceInit.put("finish_reason", null);
                initChunk.put("choices", List.of(choiceInit));
                emitter.send(initChunk);

                String content = chatCompletionService.buildMockAssistantContent(chatRequestDTO);
                String[] parts = content.split(" ");
                StringBuilder full = new StringBuilder();

                for (String part : parts) {
                    Optional<ChatCompletion> current = chatCompletionService.getCompletion(completion.getId());
                    if (current.isPresent() && current.get().isCanceled()) {
                        Map<String, Object> cancelChunk = new HashMap<>();
                        cancelChunk.put("id", completion.getId());
                        cancelChunk.put("object", "chat.completion.chunk");
                        cancelChunk.put("created", completion.getCreated().getEpochSecond());
                        cancelChunk.put("model", completion.getModel());
                        Map<String, Object> delta = Map.of("content", "[canceled]");
                        Map<String, Object> choice = new HashMap<>();
                        choice.put("index", 0);
                        choice.put("delta", delta);
                        choice.put("finish_reason", "cancel");
                        cancelChunk.put("choices", List.of(choice));
                        emitter.send(cancelChunk);

                        emitter.send("[DONE]");
                        emitter.complete();
                        return;
                    }

                    Map<String, Object> chunk = new HashMap<>();
                    chunk.put("id", completion.getId());
                    chunk.put("object", "chat.completion.chunk");
                    chunk.put("created", completion.getCreated().getEpochSecond());
                    chunk.put("model", completion.getModel());
                    Map<String, Object> delta = Map.of("content", part + " ");
                    Map<String, Object> choice = new HashMap<>();
                    choice.put("index", 0);
                    choice.put("delta", delta);
                    choice.put("finish_reason", null);
                    chunk.put("choices", List.of(choice));

                    emitter.send(chunk);
                    full.append(part).append(" ");
                    try {
                        Thread.sleep(Duration.ofSeconds(1).toMillis());
                    } catch (InterruptedException ignored) {
                    }
                }

                Map<String, Object> finalChunk = new HashMap<>();
                finalChunk.put("id", completion.getId());
                finalChunk.put("object", "chat.completion.chunk");
                finalChunk.put("created", completion.getCreated().getEpochSecond());
                finalChunk.put("model", completion.getModel());
                Map<String, Object> deltaEnd = Map.of("content", "");
                Map<String, Object> choiceEnd = new HashMap<>();
                choiceEnd.put("index", 0);
                choiceEnd.put("delta", deltaEnd);
                choiceEnd.put("finish_reason", "stop");
                finalChunk.put("choices", List.of(choiceEnd));
                emitter.send(finalChunk);

                String responseJson = chatCompletionService.buildNonStreamResponseJson(completion, full.toString().trim());
                chatCompletionService.updateResponse(completion.getId(), responseJson);

                emitter.send("[DONE]");
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
    @GetMapping("/completions/{id}")
    public ResponseEntity<?> getCompletion(@PathVariable String id) throws Exception {
        Optional  optional = chatCompletionService.getCompletion(id);
        if (optional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", Map.of("message", "Completion not found", "type", "invalid_request_error")));
        }
        ChatCompletion c = (ChatCompletion) optional.get();
        if (c.getResponseJson() != null && !c.getResponseJson().isEmpty()) {
            return ResponseEntity.ok(objectMapper.readValue(c.getResponseJson(), Map.class));
        } Map<String, Object> status = new HashMap<>();
        status.put("id", c.getId());
        status.put("object", "chat.completion");
        status.put("created", c.getCreated().getEpochSecond());
        status.put("model", c.getModel()); status.put("status", c.isCanceled() ? "canceled" : "processing");
        return ResponseEntity.ok(status);
    }
    @DeleteMapping("/completions/{id}")
    public ResponseEntity<?> deleteCompletion(@PathVariable String id) {
        Optional  optional = chatCompletionService.getCompletion(id);
        if (optional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", Map.of("message", "Completion not found", "type", "invalid_request_error")));
        }
        chatCompletionService.deleteCompletion(id);
        Map<String, Object> resp = Map.of("id", id, "object", "chat.completion", "deleted", true);
        return ResponseEntity.ok(resp);
    }
    @PostMapping("/completions/{id}/cancel")
    public ResponseEntity<?> cancelCompletion(@PathVariable String id) {
        Optional  optional = chatCompletionService.getCompletion(id);
        if (optional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", Map.of("message", "Completion not found", "type", "invalid_request_error")));
        }
        boolean canceled = chatCompletionService.cancelCompletion(id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("object", "chat.completion");
        resp.put("canceled", canceled);
        return ResponseEntity.ok(resp);
    }
}
