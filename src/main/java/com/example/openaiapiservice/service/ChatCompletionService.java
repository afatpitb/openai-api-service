package com.example.openaiapiservice.service;

import com.example.openaiapiservice.dto.ChatRequestDTO;
import com.example.openaiapiservice.model.ChatCompletion;
import com.example.openaiapiservice.repository.ChatCompletionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    public String buildNonStreamResponseJson(ChatCompletion completion, String assistantContent) throws JsonProcessingException {
        Map<String, Object> root = new HashMap<>();
        root.put("id", completion.getId());
        root.put("object", "chat.completion");
        root.put("created", completion.getCreated().getEpochSecond());
        root.put("model", completion.getModel());
        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", assistantContent);
        Map<String, Object> choice = new HashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");
        root.put("choices", java.util.List.of(choice));
        return objectMapper.writeValueAsString(root);
    }
    @Value("${openai.api.key}")
    private String apiKey;

    public String callOpenAI(ChatRequestDTO requestDTO) throws Exception {

        // 1. 构造 OpenAI 请求参数
        Map<String, Object> requestMap = new HashMap<>();

        requestMap.put("model", "gpt-4o-mini");

        java.util.List<Map<String, String>> messages = new java.util.ArrayList<>();

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        String userContent = "";

        if (requestDTO.getMessages() != null && !requestDTO.getMessages().isEmpty()) {
            userContent = requestDTO.getMessages().get(0).getContent();
        }

        userMsg.put("content", userContent);

        messages.add(userMsg);

        requestMap.put("messages", messages);

        // 2. 转 JSON
        String requestBody = objectMapper.writeValueAsString(requestMap);

        // 3. 发送 HTTP 请求
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(new java.net.URI("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        java.net.http.HttpResponse<String> response =
                client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        return response.body();
    }

    private String generateCompletionId() {
        return "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
    }

}
