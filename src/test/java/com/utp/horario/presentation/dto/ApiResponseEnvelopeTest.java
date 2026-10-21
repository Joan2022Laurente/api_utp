package com.utp.horario.presentation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de regresión del sobre estándar {@link ApiResponse}.
 *
 * <p>Verifican el cumplimiento del contrato documentado en {@code docs/ENDPOINTS.md} §1.1:
 * las cinco claves {@code success}, {@code message}, {@code data}, {@code error} y
 * {@code timestamp} deben estar siempre presentes, con la marca temporal en ISO-8601.</p>
 */
class ApiResponseEnvelopeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Una respuesta exitosa incluye las cinco claves del contrato")
    void successEnvelopeMatchesContract() throws Exception {
        ApiResponse<String> response = ApiResponse.ok("Operación exitosa", "payload");

        String json = objectMapper.writeValueAsString(response);
        var node = objectMapper.readTree(json);

        assertTrue(node.has("success"));
        assertTrue(node.has("message"));
        assertTrue(node.has("data"));
        assertTrue(node.has("error"));
        assertTrue(node.has("timestamp"), "El sobre debe incluir 'timestamp' según ENDPOINTS.md §1.1");

        assertTrue(node.get("success").asBoolean());
        assertEquals("Operación exitosa", node.get("message").asText());
        assertEquals("payload", node.get("data").asText());
        assertTrue(node.get("error").isNull());
        assertFalse(node.get("timestamp").asText().isBlank());
    }

    @Test
    @DisplayName("Una respuesta de error incluye message, error y timestamp, con data nula")
    void failureEnvelopeMatchesContract() throws Exception {
        ApiResponse<Void> response = ApiResponse.fail("Detalle técnico del error");

        String json = objectMapper.writeValueAsString(response);
        var node = objectMapper.readTree(json);

        assertFalse(node.get("success").asBoolean());
        assertEquals("Detalle técnico del error", node.get("error").asText());
        assertEquals("Detalle técnico del error", node.get("message").asText(),
                "El campo 'message' debe describir el error en las respuestas 4xx/5xx");
        assertTrue(node.get("data").isNull());
        assertFalse(node.get("timestamp").asText().isBlank());
    }

    @Test
    @DisplayName("El timestamp emitido es una marca ISO-8601 válida")
    void timestampIsIso8601() {
        ApiResponse<String> response = ApiResponse.ok("datos");

        assertNotNull(response.getTimestamp());
        Instant parsed = Instant.parse(response.getTimestamp());
        assertTrue(parsed.isBefore(Instant.now().plusSeconds(5)));
        assertTrue(parsed.isAfter(Instant.now().minusSeconds(60)));
    }

    @Test
    @DisplayName("ok(data) también documenta un mensaje legible")
    void okWithDataOnlyProvidesMessage() {
        ApiResponse<String> response = ApiResponse.ok("payload");

        assertNotNull(response.getMessage());
        assertFalse(response.getMessage().isBlank());
        assertNull(response.getError());
    }
}
