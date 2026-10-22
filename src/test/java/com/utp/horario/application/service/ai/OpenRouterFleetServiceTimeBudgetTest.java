package com.utp.horario.application.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.infrastructure.config.ai.OpenRouterProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de regresion del blindaje de tiempo de la flota OpenRouter.
 *
 * <p>El router de Heroku aborta las peticiones a los 30 s y devuelve HTTP 503. Por eso la
 * extraccion por IA tiene que estar acotada para que el cold path completo siempre
 * responda 200 con el sobre estandar y el fallback determinista.</p>
 */
class OpenRouterFleetServiceTimeBudgetTest {

    @Test
    @DisplayName("El presupuesto total de IA nunca supera los 24 s exigidos por el router de la plataforma")
    void aiBudgetNeverExceeds24Seconds() {
        assertTrue(OpenRouterFleetService.AI_TOTAL_BUDGET_MILLIS <= 24_000L,
                "El presupuesto de IA debe ser <= 24 s, pero es "
                        + OpenRouterFleetService.AI_TOTAL_BUDGET_MILLIS + " ms");
        assertTrue(OpenRouterFleetService.AI_ATTEMPT_TIMEOUT_MILLIS <= OpenRouterFleetService.AI_TOTAL_BUDGET_MILLIS,
                "El timeout por intento no puede exceder el presupuesto total");
        assertTrue(OpenRouterFleetService.AI_ATTEMPT_TIMEOUT_MILLIS >= 3_000L,
                "El timeout por intento debe ser suficiente para una respuesta util del modelo");
    }

    @Test
    @DisplayName("Sin claves configuradas la IA se desactiva de inmediato (sin consumir presupuesto)")
    void withoutKeysTheAiIsSkipped() {
        OpenRouterProperties properties = new OpenRouterProperties();
        properties.setKeys(java.util.List.of());
        ObjectMapper objectMapper = new ObjectMapper();
        OpenRouterFleetService service = new OpenRouterFleetService(
                properties, new OpenRouterModelSelector(properties, objectMapper), objectMapper);

        long start = System.currentTimeMillis();
        Optional<Syllabus> result = service.parseSyllabusWithAi("contenido", "100000SI68");
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(result.isEmpty());
        assertTrue(elapsed < 1_000L, "Sin claves la IA debereturning de inmediato (tardo " + elapsed + " ms)");
    }

    @Test
    @DisplayName("Con un endpoint de IA inalcanzable la extraccion respeta el presupuesto y cae al fallback")
    void unreachableAiRespectsTheBudget() {
        OpenRouterProperties properties = new OpenRouterProperties();
        properties.setEnabled(true);
        properties.setKeys(java.util.List.of("sk-or-v1-clave-de-prueba-0000000000000000"));
        // URL invalida: fuerza un fallo de red inmediato (no depende de la red real).
        properties.setApiUrl("http://127.0.0.1:1/unreachable");
        ObjectMapper objectMapper = new ObjectMapper();
        OpenRouterFleetService service = new OpenRouterFleetService(
                properties, new OpenRouterModelSelector(properties, objectMapper), objectMapper);

        long start = System.currentTimeMillis();
        Optional<Syllabus> result = service.parseSyllabusWithAi("contenido del sílabo", "100000SI68");
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(result.isEmpty(), "Sin respuesta de la IA debe devolverse empty para activar el fallback determinista");
        assertTrue(elapsed <= OpenRouterFleetService.AI_TOTAL_BUDGET_MILLIS + 5_000L,
                "La extraccion no debe exceder el presupuesto (+margen). Tardo " + elapsed + " ms");
        assertEquals(0, result.map(s -> 1).orElse(0));
    }
}
