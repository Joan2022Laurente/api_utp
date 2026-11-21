package com.utp.horario.presentation.controller;

import com.utp.horario.application.service.security.SessionTokenInspector;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.port.in.ScheduleServicePort;
import com.utp.horario.presentation.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Horarios y Sesiones", description = "Sincronización y consulta de horarios de clases y sesiones semanales")
@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleServicePort scheduleServicePort;

    @Operation(summary = "Consultar horario del estudiante", description = "Retorna el intervalo de horario con sesiones por día, cursos y aulas físicas.")
    @GetMapping
    public ResponseEntity<ApiResponse<ScheduleInterval>> getSchedule(
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authHeader,
            @io.swagger.v3.oas.annotations.Parameter(description = "Identificador opcional del estudiante (en peticiones con token se deriva del JWT)", example = "current-student")
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String period) {
        String token = SessionTokenInspector.extractBearerToken(authHeader);
        if (token == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("Se requiere token de sesión institucional UTP con formato 'Bearer <token>'"));
        }
        String effectiveStudentId = (studentId != null && !studentId.isBlank()) ? studentId : "current-student";
        ScheduleInterval schedule = scheduleServicePort.getStudentSchedule(effectiveStudentId, period != null ? period : "", token);
        return ResponseEntity.ok(ApiResponse.ok(schedule));
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<ScheduleInterval>> syncSchedule(
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String period) {
        String effectiveToken = SessionTokenInspector.extractBearerToken(authHeader);
        if (effectiveToken == null && token != null && !token.isBlank()) {
            effectiveToken = SessionTokenInspector.extractBearerToken("Bearer " + token.trim());
        }
        if (effectiveToken == null) {
            return ResponseEntity.status(401).body(ApiResponse.fail("Se requiere token de sesión institucional UTP con formato 'Bearer <token>'"));
        }
        ScheduleInterval synced = scheduleServicePort.syncScheduleFromUtp(effectiveToken, period != null ? period : "");
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ApiResponse.ok("Horario sincronizado con UTP", synced));
    }

    @Operation(summary = "Exportar horario en formato estándar iCalendar (.ics)", description = "Genera un archivo RFC 5545 descargable compatible con Google Calendar, Apple Calendar y Microsoft Outlook con todas las sesiones de clases del periodo.")
    @GetMapping(value = "/export.ics", produces = "text/calendar; charset=utf-8")
    public ResponseEntity<Object> exportScheduleIcs(
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authHeader,
            @io.swagger.v3.oas.annotations.Parameter(description = "Identificador opcional del estudiante (en peticiones con token se deriva del JWT)", example = "current-student")
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String period) {
        String token = SessionTokenInspector.extractBearerToken(authHeader);
        if (token == null) {
            return ResponseEntity.status(401)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.fail("Se requiere token de sesión institucional UTP con formato 'Bearer <token>'"));
        }
        String effectiveStudentId = (studentId != null && !studentId.isBlank()) ? studentId : "current-student";
        String icsContent = scheduleServicePort.exportScheduleToIcs(effectiveStudentId, period != null ? period : "", token);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"horario_utp.ics\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/calendar; charset=utf-8")
                .body(icsContent);
    }
}
