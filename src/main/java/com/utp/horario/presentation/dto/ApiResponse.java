package com.utp.horario.presentation.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Sobre estándar de respuesta de la API SyncUTP.
 *
 * <p>Contrato publicado en {@code docs/ENDPOINTS.md} §1.1:</p>
 * <pre>
 * {
 *   "success": boolean,
 *   "message": string,
 *   "data": object | null,
 *   "error": string | null,
 *   "timestamp": string (ISO-8601)
 * }
 * </pre>
 *
 * <p>Reglas de cumplimiento:</p>
 * <ul>
 *   <li>{@code success} siempre presente.</li>
 *   <li>{@code message} siempre presente: descripción legible del resultado.</li>
 *   <li>{@code data} contiene el payload en operaciones exitosas, {@code null} en errores.</li>
 *   <li>{@code error} contiene el detalle técnico en errores, {@code null} en operaciones exitosas.</li>
 *   <li>{@code timestamp} se emite siempre en ISO-8601 UTC, también en las respuestas de error.</li>
 * </ul>
 *
 * <p>La estructura del sobre no se altera: solo se completa con el campo
 * {@code timestamp} ya documentado en la especificación maestra.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private String error;

    /**
     * Marca temporal ISO-8601 (UTC) de generación de la respuesta.
     *
     * <p>Se expone mediante un getter propio que garantiza un valor no nulo en cualquier
     * vía de construcción (constructor vacío, builder o factoría).</p>
     */
    @Getter(AccessLevel.NONE)
    private String timestamp;

    public String getTimestamp() {
        String current = this.timestamp;
        if (current == null || current.isBlank()) {
            current = currentTimestamp();
            this.timestamp = current;
        }
        return current;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Operación exitosa")
                .data(data)
                .timestamp(currentTimestamp())
                .build();
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(currentTimestamp())
                .build();
    }

    public static <T> ApiResponse<T> fail(String error) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(error)
                .error(error)
                .timestamp(currentTimestamp())
                .build();
    }

    public static <T> ApiResponse<T> fail(String message, String error) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .error(error)
                .timestamp(currentTimestamp())
                .build();
    }

    private static String currentTimestamp() {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }
}
