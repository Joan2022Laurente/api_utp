package com.utp.horario.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO que consolida la evaluación de salud y disponibilidad de todas las dependencias
 * externas institucionales (SSO Keycloak, JWKS, Portal GraphQL, PAO Class, Supabase, OpenRouter).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpstreamHealthDto {

    /**
     * Estado consolidado de la pasarela:
     * - HEALTHY: Todos los servicios upstream esenciales operan normalmente.
     * - DEGRADED: Servicios operativos pero con advertencias (ej. discrepancia de emisor o fallos no críticos).
     * - DOWN: Uno o más servicios esenciales (SSO o PAO) están caídos o inaccesibles.
     */
    private String status;

    /** Resumen explicativo del diagnóstico. */
    private String summary;

    /** Marca temporal ISO-8601 de la evaluación. */
    private String timestamp;

    /** Duración total de todas las sondas concurrentes en milisegundos. */
    private long totalDurationMs;

    /** Detalle por dependencia externa evaluada. */
    private Map<String, DependencyStatus> dependencies;

    /** Lista de recomendaciones automáticas ante fallos o discrepancias. */
    private List<String> recommendations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DependencyStatus {
        private String name;
        private String url;
        private String status;          // "UP", "WARN", "DOWN", "SKIPPED"
        private Integer httpStatus;
        private Long latencyMs;
        private String detail;
        private Map<String, Object> metadata;
    }
}
