package com.utp.horario.presentation.controller;

import com.utp.horario.domain.model.CourseSummaryData;
import com.utp.horario.domain.port.in.AcademicTaskServicePort;
import com.utp.horario.presentation.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cursos y Calificaciones", description = "Resumen de cursos matriculados, fórmulas oficiales de cálculo de notas y calificaciones parciales registradas en el Portal UTP")
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseSummaryController {

    private final AcademicTaskServicePort academicTaskServicePort;

    @Operation(summary = "Obtener resumen oficial de cursos, notas parciales y fórmulas rectoras", description = "Consulta la operación GraphQL GetCourseSummary del Portal UTP con encabezados corporativos para obtener las calificaciones de cada evaluación y la fórmula oficial del ciclo.")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<CourseSummaryData>> getCourseSummary(
            @Parameter(description = "ID del periodo académico en Portal UTP (ej. 2263 para 2026-2)", example = "2263")
            @RequestParam(required = false, defaultValue = "2263") String periodId,
            @Parameter(description = "Token Bearer SSO de UTP")
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token = extractToken(authHeader);
        CourseSummaryData summary = academicTaskServicePort.getCourseSummary(periodId, token);
        return ResponseEntity.ok(ApiResponse.ok("Resumen oficial de cursos y calificaciones obtenido", summary));
    }

    @Operation(summary = "Simular nota requerida para un curso específico", description = "Calcula el puntaje acumulado actual según la fórmula oficial del curso y proyecta el promedio mínimo necesario en las evaluaciones pendientes para alcanzar la nota meta (por defecto 12.0).")
    @GetMapping("/{courseCode}/simulator")
    public ResponseEntity<ApiResponse<com.utp.horario.domain.model.GradeSimulationResult>> simulateCourseGrade(
            @Parameter(description = "Código o parte del nombre del curso (ej. 100000I69N o SERVICIOS CLOUD)", example = "100000I69N")
            @org.springframework.web.bind.annotation.PathVariable String courseCode,
            @Parameter(description = "Nota objetivo que se desea alcanzar (por defecto 12.0)", example = "12.0")
            @RequestParam(required = false, defaultValue = "12.0") double targetGrade,
            @Parameter(description = "ID del periodo académico en Portal UTP (ej. 2263 para 2026-2)", example = "2263")
            @RequestParam(required = false, defaultValue = "2263") String periodId,
            @Parameter(description = "Token Bearer SSO de UTP")
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token = extractToken(authHeader);
        com.utp.horario.domain.model.GradeSimulationResult result = academicTaskServicePort.simulateCourseGrade(courseCode, targetGrade, periodId, token);
        return ResponseEntity.ok(ApiResponse.ok("Simulación de calificación calculada exitosamente", result));
    }

    @Operation(summary = "Simular notas requeridas para todos los cursos matriculados", description = "Proyecta en paralelo para cada curso del ciclo las evaluaciones pendientes, notas acumuladas y promedios requeridos para aprobar.")
    @GetMapping("/simulator")
    public ResponseEntity<ApiResponse<java.util.List<com.utp.horario.domain.model.GradeSimulationResult>>> simulateAllCourses(
            @Parameter(description = "Nota objetivo común (por defecto 12.0)", example = "12.0")
            @RequestParam(required = false, defaultValue = "12.0") double targetGrade,
            @Parameter(description = "ID del periodo académico en Portal UTP (ej. 2263 para 2026-2)", example = "2263")
            @RequestParam(required = false, defaultValue = "2263") String periodId,
            @Parameter(description = "Token Bearer SSO de UTP")
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token = extractToken(authHeader);
        java.util.List<com.utp.horario.domain.model.GradeSimulationResult> results = academicTaskServicePort.simulateAllCourses(targetGrade, periodId, token);
        return ResponseEntity.ok(ApiResponse.ok("Simulación de todos los cursos calculada exitosamente", results));
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return authHeader;
    }
}
