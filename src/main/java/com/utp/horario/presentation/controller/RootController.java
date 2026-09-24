package com.utp.horario.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "UTP Horario Inteligente Backend API",
                "version", "1.0.0",
                "architecture", "Hexagonal / Clean Architecture (SOLID)",
                "endpoints", List.of(
                        "POST /auth/login",
                        "GET /schedule",
                        "POST /schedule/sync",
                        "GET /syllabus",
                        "GET /syllabus/{courseCode}",
                        "GET /tasks",
                        "POST /tasks/{taskId}/deliver",
                        "POST /ai/chat",
                        "GET /ai/quota"
                )
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
