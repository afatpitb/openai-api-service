package com.example.openaiapiservice.controller;

import com.example.openaiapiservice.dto.ChatRequestDTO;
import com.example.openaiapiservice.model.ChatCompletion;
import com.example.openaiapiservice.service.ChatCompletionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;


@RestController
@RequestMapping("/v1/chat")
public class OpenaiController {

    private static final java.util.Set<String> ALLOWED_MODELS = java.util.Set.of(
            "gpt-4o-mini",
            "mock-echo-001"
    );

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

        if (!ALLOWED_MODELS.contains(chatRequestDTO.getModel())) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", Map.of(
                            "message", "Invalid model",
                            "type", "invalid_request_error"
                    ))
            );
        }

        if (!rateLimiter.tryAcquire()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    Map.of("error", Map.of(
                            "message", "Too many requests",
                            "type", "rate_limit_error"
                    ))
            );
        }

        ChatCompletion completion = chatCompletionService.createCompletion(chatRequestDTO);
        boolean stream = Boolean.TRUE.equals(chatRequestDTO.getStream());

        // ✅ 非流式
        if (!stream) {
            String responseJson;

            try {
                responseJson = chatCompletionService.callOpenAI(chatRequestDTO);
            } catch (Exception e) {
                String mockContent = chatCompletionService.buildMockAssistantContent(chatRequestDTO);
                responseJson = chatCompletionService.buildNonStreamResponseJson(completion, mockContent);
            }

            chatCompletionService.updateResponse(completion.getId(), responseJson);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.readValue(responseJson, Map.class));
        }

        // ✅ 流式
        SseEmitter emitter = new SseEmitter(0L);

        new Thread(() -> {
            chatCompletionService.streamOpenAI(chatRequestDTO, emitter);
        }).start();

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
