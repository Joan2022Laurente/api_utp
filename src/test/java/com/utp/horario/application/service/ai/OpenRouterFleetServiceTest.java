package com.utp.horario.application.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.infrastructure.config.ai.OpenRouterProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenRouterFleetServiceTest {

    private OpenRouterFleetService service;

    @BeforeEach
    void setUp() {
        OpenRouterProperties properties = new OpenRouterProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        service = new OpenRouterFleetService(properties, objectMapper);
    }

    @Test
    @DisplayName("Debe manejar entrada vacía retornando Optional.empty()")
    void shouldReturnEmptyForBlankInput() {
        Optional<Syllabus> result = service.parseSyllabusWithAi("", "100000SI68");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Debe contener 12 claves en la flota de OpenRouter para rotación y fallback")
    void shouldHave12FleetKeysConfigured() {
        OpenRouterProperties properties = new OpenRouterProperties();
        properties.initFleetKeys();
        List<String> keys = properties.getKeys();
        assertNotNull(keys);
        assertEquals(12, keys.size(), "La flota debe tener exactamente 12 claves provisionadas");
        for (String k : keys) {
            assertTrue(k.startsWith("sk-or-v1-"), "Cada clave debe tener el prefijo oficial de OpenRouter");
        }
    }
}
