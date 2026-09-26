package com.utp.horario.presentation.controller;

import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.port.in.ScheduleServicePort;
import com.utp.horario.presentation.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
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
            @RequestParam(defaultValue = "current-student") String studentId,
            @RequestParam(defaultValue = "2026 - Ciclo 2 Agosto") String period) {
        String token = (authHeader != null && authHeader.startsWith("Bearer ")) 
                ? authHeader.substring(7).trim() 
                : null;
        ScheduleInterval schedule = scheduleServicePort.getStudentSchedule(studentId, period, token);
        return ResponseEntity.ok(ApiResponse.ok(schedule));
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<ScheduleInterval>> syncSchedule(
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) String token,
            @RequestParam(defaultValue = "2026 - Ciclo 2 Agosto") String period) {
        String effectiveToken = token;
        if ((effectiveToken == null || effectiveToken.isBlank()) && authHeader != null && authHeader.startsWith("Bearer ")) {
            effectiveToken = authHeader.substring(7).trim();
        }
        ScheduleInterval synced = scheduleServicePort.syncScheduleFromUtp(effectiveToken, period);
        return ResponseEntity.ok(ApiResponse.ok("Horario sincronizado con UTP", synced));
    }

    @Operation(summary = "Exportar horario en formato estándar iCalendar (.ics)", description = "Genera un archivo RFC 5545 descargable compatible con Google Calendar, Apple Calendar y Microsoft Outlook con todas las sesiones de clases del periodo.")
    @GetMapping(value = "/export.ics", produces = "text/calendar; charset=utf-8")
    public ResponseEntity<String> exportScheduleIcs(
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "current-student") String studentId,
            @RequestParam(defaultValue = "2026 - Ciclo 2 Agosto") String period) {
        String token = (authHeader != null && authHeader.startsWith("Bearer ")) 
                ? authHeader.substring(7).trim() 
                : null;
        String icsContent = scheduleServicePort.exportScheduleToIcs(studentId, period, token);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"horario_utp.ics\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/calendar; charset=utf-8")
                .body(icsContent);
    }
}
