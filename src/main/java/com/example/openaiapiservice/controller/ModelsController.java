package com.example.openaiapiservice.controller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
public class ModelsController {
    @GetMapping("/models")
    public ResponseEntity<?> listModels() {
        Map<String, Object> m1 = Map.of( "id", "gpt-4o-mini", "object", "model", "created", Instant.now().getEpochSecond(), "owned_by", "organization" );
        Map<String, Object> m2 = Map.of( "id", "mock-echo-001", "object", "model", "created", Instant.now().getEpochSecond(), "owned_by", "local" );
        Map<String, Object> root = Map.of("object", "list", "data", List.of(m1, m2));
        return ResponseEntity.ok(root);
    }
}