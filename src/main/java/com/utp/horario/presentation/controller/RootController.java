package com.utp.horario.presentation.controller;

import com.utp.horario.application.service.health.UpstreamHealthService;
import com.utp.horario.presentation.dto.ApiResponse;
import com.utp.horario.presentation.dto.UpstreamHealthDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Salud y Diagnóstico", description = "Endpoints de estado de la aplicación y diagnóstico en tiempo real de dependencias externas")
@RestController
@RequiredArgsConstructor
public class RootController {

    private final UpstreamHealthService upstreamHealthService;

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "app", "SyncUTP",
                "service", "SyncUTP API - Pasarela Académica y Core Rector UTP",
                "version", "2.0.0",
                "architecture", "Hexagonal / Clean Architecture (SOLID)",
                "documentation", "/api/v1/swagger-ui/index.html",
                "endpoints", List.of(
                        "GET  /",
                        "POST /auth/login",
                        "POST /auth/refresh",
                        "GET  /auth/me",
                        "GET  /auth/profile/{id}",
                        "GET  /schedule",
                        "POST /schedule/sync",
                        "GET  /schedule/export.ics",
                        "GET  /sections/{sectionId}/full",
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
                        "GET  /health",
                        "GET  /health/upstream"
                )
        ));
    }

    @Operation(summary = "Liveness probe básico", description = "Comprueba que la pasarela local de Spring Boot se encuentra viva y en ejecución.")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @Operation(summary = "Diagnóstico y salud upstream", description = "Sondea concurrentemente en tiempo real la disponibilidad y latencia de Keycloak SSO, JWKS, Portal UTP GraphQL, UTP+ Class PAO, Supabase y OpenRouter AI.")
    @GetMapping("/health/upstream")
    public ResponseEntity<ApiResponse<UpstreamHealthDto>> upstreamHealth() {
        UpstreamHealthDto healthDto = upstreamHealthService.evaluateUpstreamHealth();
        return ResponseEntity.ok(ApiResponse.ok("Diagnóstico de servicios upstream completado", healthDto));
    }
}
