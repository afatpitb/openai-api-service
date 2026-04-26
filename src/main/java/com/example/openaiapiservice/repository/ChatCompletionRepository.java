package com.example.openaiapiservice.repository;

import com.example.openaiapiservice.model.ChatCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatCompletionRepository extends JpaRepository<ChatCompletion, String> {
}
