package com.utp.horario.presentation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.utp.horario.application.service.security.SessionTokenInspector;
import com.utp.horario.domain.port.in.AcademicTaskServicePort;
import com.utp.horario.presentation.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consulta del árbol canónico de contenidos de una sección de UTP+ Class. */
@Tag(name = "Secciones", description = "Árbol completo de contenidos y actividades del aula virtual")
@RestController
@RequestMapping("/sections")
@RequiredArgsConstructor
public class SectionController {

    private final AcademicTaskServicePort academicTaskServicePort;

    @Operation(summary = "Obtener árbol completo de contenidos y actividades de una sección",
            description = "Consulta la estructura unities -> themes -> contents de UTP+ Class.")
    @GetMapping("/{sectionId}/full")
    public ResponseEntity<ApiResponse<JsonNode>> getSectionFull(
            @Parameter(description = "Identificador de la sección en UTP+ Class")
            @PathVariable String sectionId,
            @Parameter(description = "Token Bearer SSO de UTP")
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token = SessionTokenInspector.extractBearerToken(authHeader);
        if (token == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail(
                    "Se requiere token de sesión institucional UTP con formato 'Bearer <token>'"));
        }

        JsonNode sectionTree = academicTaskServicePort.getSectionFull(sectionId, token);
        return ResponseEntity.ok(ApiResponse.ok("Árbol de la sección obtenido exitosamente", sectionTree));
    }
}
