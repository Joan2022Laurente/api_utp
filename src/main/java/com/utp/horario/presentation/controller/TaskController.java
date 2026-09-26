package com.utp.horario.presentation.controller;

import com.utp.horario.domain.model.AcademicActivity;
import com.utp.horario.domain.model.TaskSpecification;
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

import java.util.List;

@Tag(name = "Tareas y Evaluaciones", description = "Endpoints para consulta de especificaciones de tareas, rúbricas multinivel y calendario de entregas")
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final AcademicTaskServicePort academicTaskServicePort;

    @Operation(summary = "Obtener especificación detallada de una tarea o evaluación", description = "Retorna la consigna en Markdown, formato de entrega, intentos permitidos, fechas límites y rúbrica completa con niveles de puntaje.")
    @GetMapping("/{sectionId}/{activityId}")
    public ResponseEntity<ApiResponse<TaskSpecification>> getTaskDetail(
            @Parameter(description = "UUID de la sección en UTP+ Class", example = "7eddf3c8-98eb-5d6e-a295-856ce4ee6c3c")
            @PathVariable String sectionId,
            @Parameter(description = "UUID de la actividad o homeworkId", example = "0c621204-1014-59be-aece-5664fb6e32a0")
            @PathVariable String activityId,
            @Parameter(description = "Token Bearer SSO de UTP")
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token = extractToken(authHeader);
        TaskSpecification task = academicTaskServicePort.getTaskDetail(sectionId, activityId, token);
        return ResponseEntity.ok(ApiResponse.ok(task));
    }

    @Operation(summary = "Consultar calendario unificado de actividades del ciclo", description = "Obtiene todas las actividades (foros, tareas, prácticas) programadas en el periodo con sus estados, fechas de entrega y filtros opcionales.")
    @GetMapping("/activities")
    public ResponseEntity<ApiResponse<List<AcademicActivity>>> getCalendarActivities(
            @Parameter(description = "Fecha base para consulta (yyyy-MM-dd+00:00:00)")
            @RequestParam(required = false, defaultValue = "2026-09-21+00:00:00") String dateToQuery,
            @Parameter(description = "Modo de intervalo ('period', 'week', 'day')")
            @RequestParam(required = false, defaultValue = "period") String intervalMode,
            @Parameter(description = "Filtrar por número de semana académica (ej. 7)")
            @RequestParam(required = false) Integer week,
            @Parameter(description = "Filtrar por estado de entrega ('PENDING', 'DELIVERED', 'MISSING', 'PROGRAMMED')")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filtrar únicamente evaluaciones calificadas / ponderadas (true/false)")
            @RequestParam(required = false) Boolean onlyGraded,
            @Parameter(description = "Filtrar por tipo de actividad ('HOMEWORK', 'FORUM', 'EVALUATION')")
            @RequestParam(required = false) String type,
            @Parameter(description = "Token Bearer SSO de UTP")
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token = extractToken(authHeader);
        List<AcademicActivity> activities = academicTaskServicePort.getCalendarActivitiesFiltered(
                dateToQuery, intervalMode, token, week, status, onlyGraded, type);
        return ResponseEntity.ok(ApiResponse.ok("Actividades de calendario obtenidas exitosamente", activities));
    }

    @Operation(summary = "Obtener las próximas evaluaciones ponderadas", description = "Filtra cronológicamente las próximas evaluaciones de todos los cursos que tienen impacto en el promedio final ponderado.")
    @GetMapping("/upcoming")
    public ResponseEntity<ApiResponse<List<AcademicActivity>>> getUpcomingEvaluations(
            @Parameter(description = "Cantidad máxima de evaluaciones a retornar")
            @RequestParam(required = false, defaultValue = "5") int limit,
            @Parameter(description = "Token Bearer SSO de UTP")
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String token = extractToken(authHeader);
        List<AcademicActivity> upcoming = academicTaskServicePort.getUpcomingEvaluations(token, limit);
        return ResponseEntity.ok(ApiResponse.ok("Próximas evaluaciones ponderadas obtenidas", upcoming));
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return authHeader;
    }
}
