package com.utp.horario.presentation.controller;

import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.port.in.SyllabusServicePort;
import com.utp.horario.presentation.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Sílabos Oficiales", description = "Extracción, procesamiento posicional y persistencia de sílabos oficiales rectores UTP")
@RestController
@RequestMapping("/syllabus")
@RequiredArgsConstructor
public class SyllabusController {

    private final SyllabusServicePort syllabusServicePort;

    @Operation(summary = "Obtener sílabo estructurado por código de curso", description = "Descarga o recupera de BD el sílabo rector con desglose semanal, fórmulas y unidades.")
    @GetMapping("/{courseCode}")
    public ResponseEntity<ApiResponse<Syllabus>> getSyllabus(
            @Parameter(description = "Código del curso (ej. 100000ST61)")
            @PathVariable String courseCode,
            @Parameter(description = "ID de la sección en Class (opcional)")
            @RequestParam(required = false) String sectionId,
            @Parameter(description = "URL directa del PDF en S3 (opcional)")
            @RequestParam(required = false) String pdfUrl,
            @Parameter(description = "Token Bearer SSO de UTP")
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        Syllabus syllabus = syllabusServicePort.getSyllabus(courseCode, sectionId, pdfUrl, token);
        return ResponseEntity.ok(ApiResponse.ok(syllabus));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Syllabus>>> getAllSyllabi(@RequestParam(defaultValue = "current-student") String studentId) {
        List<Syllabus> list = syllabusServicePort.getAllSyllabiForStudent(studentId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/parse")
    public ResponseEntity<ApiResponse<Syllabus>> parseSyllabus(
            @RequestParam String courseCode,
            @RequestBody String syllabusText) {
        Syllabus parsed = syllabusServicePort.parseAndSaveSyllabusText(courseCode, syllabusText);
        return ResponseEntity.ok(ApiResponse.ok("Sílabo procesado correctamente", parsed));
    }

    @PostMapping("/save")
    public ResponseEntity<ApiResponse<Syllabus>> saveSyllabus(@RequestBody Syllabus syllabus) {
        Syllabus saved = syllabusServicePort.saveSyllabus(syllabus);
        return ResponseEntity.ok(ApiResponse.ok("Sílabo guardado exitosamente en base de datos", saved));
    }

    @GetMapping("/raw-text")
    public ResponseEntity<ApiResponse<String>> getRawText(
            @RequestParam String courseCode,
            @RequestParam(required = false) String sectionId,
            @RequestParam(required = false) String pdfUrl,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String rawText = syllabusServicePort.fetchRawSyllabusText(courseCode, sectionId, pdfUrl, token);
        return ResponseEntity.ok(ApiResponse.ok("Texto de sílabo obtenido", rawText));
    }

    @Operation(summary = "Obtener sílabo en formato Markdown para LLMs y Prompt Engineering", description = "Genera una versión en Markdown estructurado del sílabo oficial del PDF para inyección directa en prompts de LLMs (OpenAI, Gemini, Claude, DeepSeek).")
    @GetMapping(value = "/{courseCode}/markdown", produces = "text/markdown; charset=utf-8")
    public ResponseEntity<String> getSyllabusMarkdown(
            @Parameter(description = "Código del curso (ej. 100000ST61)")
            @PathVariable String courseCode,
            @Parameter(description = "ID de la sección en Class (opcional)")
            @RequestParam(required = false) String sectionId,
            @Parameter(description = "URL directa del PDF en S3 (opcional)")
            @RequestParam(required = false) String pdfUrl,
            @Parameter(description = "Si es true, fuerza la conversión de texto crudo sin estructuración previa")
            @RequestParam(required = false, defaultValue = "false") boolean preferRaw,
            @Parameter(description = "Token Bearer SSO de UTP")
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
        String markdown = syllabusServicePort.getSyllabusAsMarkdown(courseCode, sectionId, pdfUrl, token, preferRaw);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/markdown; charset=utf-8")
                .body(markdown);
    }

    @Operation(summary = "Obtener sílabo en formato Markdown mediante query params", description = "Permite consultar el sílabo en Markdown pasando el código de curso como parámetro ?courseCode=...")
    @GetMapping(value = "/markdown", produces = "text/markdown; charset=utf-8")
    public ResponseEntity<String> getMarkdownByParam(
            @RequestParam String courseCode,
            @RequestParam(required = false) String sectionId,
            @RequestParam(required = false) String pdfUrl,
            @RequestParam(required = false, defaultValue = "false") boolean preferRaw,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return getSyllabusMarkdown(courseCode, sectionId, pdfUrl, preferRaw, authHeader);
    }
}
