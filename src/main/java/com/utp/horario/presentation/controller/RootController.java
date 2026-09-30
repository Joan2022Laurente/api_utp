package com.utp.horario.presentation.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
public class RootController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<org.springframework.core.io.Resource> indexHtml() {
        org.springframework.core.io.Resource resource = new org.springframework.core.io.ClassPathResource("static/index.html");
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(resource);
    }

    @GetMapping(value = "/", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.ALL_VALUE})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "app", "SyncUTP",
                "service", "SyncUTP API - Pasarela Académica y Core Rector UTP",
                "version", "2.0.0",
                "architecture", "Hexagonal / Clean Architecture (SOLID)",
                "documentation", "/api/v1/swagger-ui/index.html",
                "endpoints", List.of(
                        "POST /auth/login",
                        "POST /auth/refresh",
                        "GET  /auth/me",
                        "GET  /auth/profile/{id}",
                        "GET  /schedule",
                        "POST /schedule/sync",
                        "GET  /schedule/export.ics",
                        "GET  /syllabus",
                        "GET  /syllabus/{courseCode}",
                        "GET  /syllabus/{courseCode}/markdown",
                        "GET  /syllabus/raw-text",
                        "GET  /tasks/{sectionId}/{activityId}",
                        "GET  /tasks/activities",
                        "GET  /tasks/upcoming",
                        "GET  /courses/summary",
                        "GET  /courses/{courseCode}/simulator",
                        "GET  /courses/simulator",
                        "GET  /health"
                )
        ));
    }

    @GetMapping("/health")
    @ResponseBody
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
