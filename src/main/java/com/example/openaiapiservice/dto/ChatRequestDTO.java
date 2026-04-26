package com.example.openaiapiservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class ChatRequestDTO {

    @NotNull(message = "Model cannot be null")
    private String model;

    @NotNull(message = "Messages cannot be null")
    @Size(min = 1, message = "Messages must contain at least one message")
    private List<Message> messages;

    private Boolean stream = false;

    private Double temperature = 1.0;

    public static class Message {
        @NotNull(message = "Role cannot be null")
        private String role;

        @NotNull(message = "Content cannot be null")
        private String content;

        public String getRole() {
            return role;
        }
        public void setRole(String role) {
            this.role = role;
        }
        public String getContent() {
            return content;
        }
        public void setContent(String content) {
            this.content = content;
        }
    }

    public String getModel() {
        return model;
    }
    public void setModel(String model) {
        this.model = model;
    }
    public List<Message> getMessages() {
        return messages;
    }
    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }
    public Boolean getStream() {
        return stream;
    }
    public void setStream(Boolean stream) {
        this.stream = stream;
    }
    public Double getTemperature() {
        return temperature;
    }
    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }
}

