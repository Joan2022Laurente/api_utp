package com.utp.horario.presentation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.utp.horario.application.service.security.SessionTokenInspector;
import com.utp.horario.domain.model.MaterialTextExtraction;
import com.utp.horario.domain.model.SectionCurriculumTree;
import com.utp.horario.domain.model.WeekAcademicContext;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta del árbol canónico de contenidos, agregador semanal y extracción de materiales de UTP+ Class. */
@Tag(name = "Secciones y Contenidos", description = "Árbol completo de contenidos, dossier contextual por semana y extracción de texto de materiales de S3")
@RestController
@RequestMapping("/sections")
@RequiredArgsConstructor
public class SectionController {

    private final AcademicTaskServicePort academicTaskServicePort;

    @Operation(summary = "Obtener árbol completo de contenidos y actividades de una sección (crudo)",
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

    @Operation(summary = "Obtener currículo tipado y normalizado de la sección",
            description = "Devuelve el árbol normalizado units -> weeks -> topics -> materials con URLs de S3.")
    @GetMapping("/{sectionId}/curriculum")
    public ResponseEntity<ApiResponse<SectionCurriculumTree>> getSectionCurriculum(
            @Parameter(description = "Identificador de la sección en UTP+ Class")
            @PathVariable String sectionId,
            @Parameter(description = "Token Bearer SSO de UTP")
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token = SessionTokenInspector.extractBearerToken(authHeader);
        if (token == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail(
                    "Se requiere token de sesión institucional UTP con formato 'Bearer <token>'"));
        }

        SectionCurriculumTree tree = academicTaskServicePort.getSectionCurriculum(sectionId, token);
        return ResponseEntity.ok(ApiResponse.ok("Currículo de la sección obtenido exitosamente", tree));
    }

    @Operation(summary = "Obtener dossier contextual de una semana académica (Sílabo + Aula + Tareas)",
            description = "Agregador para agentes IA y MCP: consolida logros del sílabo, materiales de S3 y tareas con rúbricas en una sola llamada sin consultas N+1.")
    @GetMapping("/{sectionId}/weeks/{weekNumber}/context")
    public ResponseEntity<ApiResponse<WeekAcademicContext>> getWeekContext(
            @Parameter(description = "Identificador de la sección en UTP+ Class")
            @PathVariable String sectionId,
            @Parameter(description = "Número de semana académica (1 a 18)")
            @PathVariable int weekNumber,
            @Parameter(description = "Token Bearer SSO de UTP")
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token = SessionTokenInspector.extractBearerToken(authHeader);
        if (token == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail(
                    "Se requiere token de sesión institucional UTP con formato 'Bearer <token>'"));
        }

        WeekAcademicContext context = academicTaskServicePort.getWeekContext(sectionId, weekNumber, token);
        return ResponseEntity.ok(ApiResponse.ok("Contexto académico de la semana " + weekNumber + " obtenido", context));
    }

    @Operation(summary = "Extraer texto de un material de clase (PDF) en Markdown",
            description = "Descarga segura desde AWS S3 con validación anti-SSRF y extracción de texto por páginas con límite de caracteres para no saturar contexto de LLMs.")
    @GetMapping("/{sectionId}/materials/{materialId}/text")
    public ResponseEntity<ApiResponse<MaterialTextExtraction>> extractMaterialText(
            @Parameter(description = "Identificador de la sección en UTP+ Class")
            @PathVariable String sectionId,
            @Parameter(description = "Identificador o UUID del material en el árbol")
            @PathVariable String materialId,
            @Parameter(description = "Rango de páginas (ej. '1-10', '5-8' o '3')")
            @RequestParam(required = false, defaultValue = "1-10") String pages,
            @Parameter(description = "Límite máximo de caracteres extraídos (default: 8000)")
            @RequestParam(required = false, defaultValue = "8000") int maxChars,
            @Parameter(description = "Token Bearer SSO de UTP")
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token = SessionTokenInspector.extractBearerToken(authHeader);
        if (token == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail(
                    "Se requiere token de sesión institucional UTP con formato 'Bearer <token>'"));
        }

        MaterialTextExtraction extraction = academicTaskServicePort.extractMaterialText(sectionId, materialId, pages, maxChars, token);
        return ResponseEntity.ok(ApiResponse.ok("Texto de material extraído exitosamente", extraction));
    }
}
