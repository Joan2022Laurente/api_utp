package com.utp.horario.application.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.infrastructure.config.ai.OpenRouterProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRouterModelSelectorTest {

    private OpenRouterModelSelector modelSelector;

    @BeforeEach
    void setUp() {
        OpenRouterProperties properties = new OpenRouterProperties();
        properties.initFleetKeys();
        ObjectMapper objectMapper = new ObjectMapper();
        modelSelector = new OpenRouterModelSelector(properties, objectMapper);
    }

    @Test
    @DisplayName("Debe rankear dinámicamente modelos gratuitos válidos para tareas de extracción técnica")
    void shouldDiscoverAndRankFreeModels() {
        List<String> ranked = modelSelector.getRankedFreeModels(4);

        assertNotNull(ranked);
        assertFalse(ranked.isEmpty(), "Debe retornar al menos los modelos fallbacks o descubiertos");
        assertTrue(ranked.size() <= 4);

        // Ningún modelo debe ser de seguridad o moderación
        for (String m : ranked) {
            String lower = m.toLowerCase();
            assertFalse(lower.contains("safety"), "No debe incluir filtros de seguridad");
            assertFalse(lower.contains("moderation"), "No debe incluir modelos de moderación");
            assertFalse(lower.contains("embed"), "No debe incluir modelos de embedding");
        }
    }
}
