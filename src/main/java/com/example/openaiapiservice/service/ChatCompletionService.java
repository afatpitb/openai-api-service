package com.example.openaiapiservice.service;

import com.example.openaiapiservice.dto.ChatRequestDTO;
import com.example.openaiapiservice.model.ChatCompletion;
import com.example.openaiapiservice.repository.ChatCompletionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;

@Service

public class ChatCompletionService {
    private final ChatCompletionRepository repository;
    private final ObjectMapper objectMapper;
    public ChatCompletionService(ChatCompletionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }
    public ChatCompletion createCompletion(ChatRequestDTO requestDTO) throws JsonProcessingException {
        ChatCompletion completion = new ChatCompletion();
        completion.setId(generateCompletionId());
        completion.setModel(requestDTO.getModel());
        completion.setCreated(Instant.now());
        completion.setCanceled(false);
        completion.setRequestJson(objectMapper.writeValueAsString(requestDTO));
        repository.save(completion);
        return completion;
    }
    public void updateResponse(String id, String responseJson) {
        Optional  optional = repository.findById(id);
        if (optional.isPresent()) {
            ChatCompletion completion = (ChatCompletion) optional.get();
            completion.setResponseJson(responseJson);
            repository.save(completion);
        }
    }
    public void streamOpenAI(ChatRequestDTO requestDTO, SseEmitter emitter) {
        try {
            if (apiKey == null || apiKey.isEmpty()) {
                // fallback mock
                String content = buildMockAssistantContent(requestDTO);
                for (char c : content.toCharArray()) {
                    emitter.send(SseEmitter.event().data(
                            Map.of("choices", List.of(
                                    Map.of("delta", Map.of("content", String.valueOf(c)))
                            ))
                    ));
                    Thread.sleep(30);
                }
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
                return;
            }

            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("model", requestDTO.getModel());
            requestMap.put("stream", true);

            List<Map<String, String>> messages = new ArrayList<>();
            for (ChatRequestDTO.Message msg : requestDTO.getMessages()) {
                messages.add(Map.of(
                        "role", msg.getRole(),
                        "content", msg.getContent()
                ));
            }

            requestMap.put("messages", messages);

            String body = objectMapper.writeValueAsString(requestMap);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<InputStream> response =
                    client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body())
            );

            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);

                    if ("[DONE]".equals(data)) {
                        emitter.send(SseEmitter.event().data("[DONE]"));
                        break;
                    }

                    emitter.send(SseEmitter.event().data(data));
                }
            }

            emitter.complete();

        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    public Optional  getCompletion(String id) {
        return repository.findById(id);
    }
    public void deleteCompletion(String id) {

        repository.deleteById(id);
    }
    public boolean cancelCompletion(String id) {
        Optional  optional = repository.findById(id);
        if (optional.isPresent()) {
            ChatCompletion completion = (ChatCompletion) optional.get();
            if (!completion.isCanceled()) {
                completion.setCanceled(true);
                repository.save(completion);
                return true;
            }
        }
        return false;
    }

    public String buildMockAssistantContent(ChatRequestDTO requestDTO) {
        int count = requestDTO.getMessages() == null ? 0 : requestDTO.getMessages().size();
        String lastUser = "";
        if (requestDTO.getMessages() != null) {
            for (ChatRequestDTO.Message m : requestDTO.getMessages()) {
                if ("user".equalsIgnoreCase(m.getRole())) {
                    lastUser = m.getContent();
                }
            }
        }
        return "Echo: model=" + requestDTO.getModel() + ", temperature=" + requestDTO.getTemperature() + ", messages=" + count + ", last_user_message=" + lastUser + "";
    }
    public String buildNonStreamResponseJson(ChatCompletion completion, String content) throws Exception {
        Map<String, Object> response = new HashMap<>();

        response.put("id", completion.getId());
        response.put("object", "chat.completion");
        response.put("created", System.currentTimeMillis() / 1000);
        response.put("model", "gpt-4o-mini-2024-07-18");

        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", content);

        Map<String, Object> choice = new HashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");

        response.put("choices", List.of(choice));

        return objectMapper.writeValueAsString(response);
    }
    private String buildMockResponse(ChatRequestDTO requestDTO) throws Exception {
        String content = "Echo: ";

        if (requestDTO.getMessages() != null && !requestDTO.getMessages().isEmpty()) {
            content += requestDTO.getMessages()
                    .get(requestDTO.getMessages().size() - 1)
                    .getContent();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", "chatcmpl-mock");
        response.put("object", "chat.completion");
        response.put("created", System.currentTimeMillis() / 1000);
        response.put("model", "mock-echo-001");

        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", content);

        Map<String, Object> choice = new HashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");

        response.put("choices", List.of(choice));

        return objectMapper.writeValueAsString(response);
    }


    @Value("${openai.api.key:}")
    private String apiKey;

    public String callOpenAI(ChatRequestDTO requestDTO) throws Exception {

        // 👉 1. 如果没有 API key，直接 mock
        if (apiKey == null || apiKey.isEmpty()) {
            return buildMockResponse(requestDTO);
        }

        try {
            // 👉 2. 构造请求
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("model", requestDTO.getModel());

            List<Map<String, String>> messages = new ArrayList<>();

            // ✅ 修复：遍历所有 messages（不是只取第一条）
            if (requestDTO.getMessages() != null) {
                for (ChatRequestDTO.Message msg : requestDTO.getMessages()) {
                    Map<String, String> m = new HashMap<>();
                    m.put("role", msg.getRole());
                    m.put("content", msg.getContent());
                    messages.add(m);
                }
            }

            requestMap.put("messages", messages);

            String requestBody = objectMapper.writeValueAsString(requestMap);

            // 👉 3. 调用 OpenAI
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // 👉 4. 如果失败 → fallback
            if (response.statusCode() != 200) {
                return buildMockResponse(requestDTO);
            }

            return response.body();

        } catch (Exception e) {
            // 👉 5. 异常 → fallback
            return buildMockResponse(requestDTO);
        }
    }

    private String generateCompletionId() {
        return "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
    }

}
