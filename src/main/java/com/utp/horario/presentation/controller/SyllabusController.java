package com.utp.horario.presentation.controller;

import com.utp.horario.domain.model.Course;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.port.in.ScheduleServicePort;
import com.utp.horario.domain.port.in.SyllabusServicePort;
import com.utp.horario.presentation.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "Sílabos Oficiales", description = "Extracción, procesamiento posicional y persistencia de sílabos oficiales rectores UTP")
@RestController
@RequestMapping("/syllabus")
@RequiredArgsConstructor
@Slf4j
public class SyllabusController {

    private final SyllabusServicePort syllabusServicePort;
    private final ScheduleServicePort scheduleServicePort;

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

    /**
     * Catalogo de silabos.
     *
     * <p>Por defecto ({@code scope=all}) se mantiene el comportamiento historico: se
     * devuelve el catalogo completo. El alcance {@code scope=student} es unavia optativa
     * que restrictse el resultado a los cursos matriculados por el estudiante indicado,
     * sin alterar el contrato existente.</p>
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Syllabus>>> getAllSyllabi(
            @RequestParam(defaultValue = "current-student") String studentId,
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(defaultValue = "2026 - Ciclo 2 Agosto") String period,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        List<Syllabus> list = syllabusServicePort.getAllSyllabiForStudent(studentId);

        if ("student".equalsIgnoreCase(scope)) {
            String token = authHeader != null && authHeader.startsWith("Bearer ")
                    ? authHeader.substring(7).trim()
                    : null;
            List<Course> enrolled = enrolledCourses(studentId, period, token);
            Set<String> enrolledCodes = enrolledCodes(enrolled);
            Set<String> enrolledNames = enrolledNames(enrolled);
            List<Syllabus> filtered = list.stream()
                    .filter(syllabus -> matchesEnrollment(syllabus, enrolledCodes, enrolledNames))
                    .toList();
            return ResponseEntity.ok(ApiResponse.ok(filtered));
        }

        log.debug("[SyllabusController] Catalogo completo solicitado (scope={}, studentId={}).",
                scope, studentId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * Indica si el silabo corresponde a un curso en el que el estudiante esta matriculado.
     *
     * <p>El portal y el catalogo de silabos no comparten el mismo codigo de curso (por
     * ejemplo, el portal usa {@code 100000Desa} y el silabo {@code 100000ST61}), por lo que
     * la correspondencia se resuelve tambien por nombre normalizado, sin acentos ni
     * mayusculas.</p>
     */
    private boolean matchesEnrollment(Syllabus syllabus, Set<String> enrolledCodes, Set<String> enrolledNames) {
        if (syllabus == null) {
            return false;
        }
        String code = syllabus.getCourseCode();
        if (code != null && enrolledCodes.contains(code.trim().toUpperCase())) {
            return true;
        }
        String name = syllabus.getCourseName();
        return name != null && enrolledNames.contains(normalizeCourseName(name));
    }

    /**
     * Obtiene los codigos de curso en los que el estudiante esta matriculado.
     */
    private Set<String> enrolledCodes(List<Course> courses) {
        return courses.stream()
                .map(Course::getCode)
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase())
                .collect(Collectors.toSet());
    }

    /**
     * Obtiene los nombres de curso (normalizados) en los que el estudiante esta matriculado.
     */
    private Set<String> enrolledNames(List<Course> courses) {
        return courses.stream()
                .map(Course::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(this::normalizeCourseName)
                .collect(Collectors.toSet());
    }

    /**
     * Cursos del horario del estudiante.
     */
    private List<Course> enrolledCourses(String studentId, String period, String token) {
        ScheduleInterval schedule = scheduleServicePort.getStudentSchedule(studentId, period, token);
        if (schedule == null || schedule.getCourses() == null) {
            return List.of();
        }
        return schedule.getCourses();
    }

    /**
     * Normaliza un nombre de curso para comparar: sin acentos, en mayusculas y sin espacios
     * redundantes.
     */
    private String normalizeCourseName(String name) {
        return java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .trim()
                .toUpperCase(java.util.Locale.ROOT);
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
