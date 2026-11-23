package com.utp.horario.presentation.controller;

import com.utp.horario.application.service.security.SessionTokenInspector;
import com.utp.horario.domain.exception.AuthenticationException;
import com.utp.horario.domain.model.Course;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.port.in.ScheduleServicePort;
import com.utp.horario.domain.port.in.SyllabusServicePort;
import com.utp.horario.domain.util.CourseNameNormalizer;
import com.utp.horario.presentation.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@RequestMapping({"/syllabus", "/syllabuses"})
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
        String token = optionalBearer(authHeader);
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
            @Parameter(description = "Identificador opcional del estudiante (en peticiones con token se deriva del JWT)")
            @RequestParam(required = false) String studentId,
            @Parameter(description = "Alcance del catálogo: 'all' (todos los sílabos) o 'student' (filtrado por matrícula del estudiante)", example = "all")
            @RequestParam(defaultValue = "all") String scope,
            @Parameter(description = "Periodo académico tal como lo devuelve /academicperiods (campo 'name'). Si se omite, se resuelve dinámicamente del token.")
            @RequestParam(required = false) String period,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String tokenOpt = SessionTokenInspector.extractBearerToken(authHeader);
        String effectiveStudentId = (studentId != null && !studentId.isBlank())
                ? studentId
                : (tokenOpt != null && SessionTokenInspector.tryInspect(tokenOpt).isPresent()
                        ? SessionTokenInspector.inspect(tokenOpt).studentCode() : "");
        List<Syllabus> list = syllabusServicePort.getAllSyllabiForStudent(effectiveStudentId);

        if ("student".equalsIgnoreCase(scope)) {
            String token = SessionTokenInspector.extractBearerToken(authHeader);
            if (token == null) {
                return ResponseEntity.status(401).body(ApiResponse.fail("Se requiere token de sesión institucional UTP con formato 'Bearer <token>' para filtrar por estudiante"));
            }
            List<Course> enrolled = enrolledCourses(effectiveStudentId, period != null ? period : "", token);
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
        return name != null && enrolledNames.contains(CourseNameNormalizer.normalize(name));
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
                .map(CourseNameNormalizer::normalize)
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
     * Extrae un Bearer opcional. Si el cliente envía una cabecera, debe ser un JWT Bearer
     * válido; si no envía ninguna, se permite el acceso anónimo de solo caché.
     *
     * @throws AuthenticationException si la cabecera existe pero no es un Bearer JWT válido
     */
    private String optionalBearer(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }
        String token = SessionTokenInspector.extractBearerToken(authHeader);
        if (token == null) {
            throw new AuthenticationException(
                    "Cabecera Authorization inválida: se requiere el formato 'Bearer <token>' con un JWT vigente.");
        }
        return token;
    }

    @GetMapping("/raw-text")
    public ResponseEntity<ApiResponse<String>> getRawText(
            @RequestParam String courseCode,
            @RequestParam(required = false) String sectionId,
            @RequestParam(required = false) String pdfUrl,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (courseCode == null || courseCode.trim().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("Se requiere el parámetro courseCode"));
        }
        String token = optionalBearer(authHeader);
        String rawText = syllabusServicePort.fetchRawSyllabusText(courseCode.trim(), sectionId, pdfUrl, token);
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
        String token = optionalBearer(authHeader);
        String markdown = syllabusServicePort.getSyllabusAsMarkdown(courseCode, sectionId, pdfUrl, token, preferRaw);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/markdown; charset=utf-8")
                .body(markdown);
    }
}
