package com.utp.horario.application.service.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.infrastructure.config.UtpProperties;
import com.utp.horario.infrastructure.config.ai.OpenRouterProperties;
import com.utp.horario.infrastructure.config.supabase.SupabaseProperties;
import com.utp.horario.infrastructure.security.JwtVerificationProperties;
import com.utp.horario.presentation.dto.UpstreamHealthDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamHealthServiceTest {

    private UpstreamHealthService healthService;
    private JwtVerificationProperties jwtProperties;
    private UtpProperties utpProperties;
    private SupabaseProperties supabaseProperties;
    private OpenRouterProperties openRouterProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtVerificationProperties();
        utpProperties = new UtpProperties();
        supabaseProperties = new SupabaseProperties();
        supabaseProperties.setEnabled(false); // Evitar llamadas en tests unitarios si no hay credenciales
        openRouterProperties = new OpenRouterProperties();
        openRouterProperties.setEnabled(false);

        healthService = new UpstreamHealthService(
                jwtProperties,
                utpProperties,
                supabaseProperties,
                openRouterProperties,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("evaluateUpstreamHealth evalúa todas las dependencias y retorna DTO estructurado")
    void evaluateUpstreamHealth_executesGracefully() {
        UpstreamHealthDto dto = healthService.evaluateUpstreamHealth();

        assertNotNull(dto);
        assertNotNull(dto.getStatus());
        assertNotNull(dto.getSummary());
        assertNotNull(dto.getTimestamp());
        assertTrue(dto.getTotalDurationMs() >= 0);
        assertNotNull(dto.getDependencies());

        // Verificar que las llaves esenciales existen en el mapa de dependencias
        assertTrue(dto.getDependencies().containsKey("sso_discovery"));
        assertTrue(dto.getDependencies().containsKey("sso_jwks_certs"));
        assertTrue(dto.getDependencies().containsKey("portal_graphql"));
        assertTrue(dto.getDependencies().containsKey("pao_gateway"));
        assertTrue(dto.getDependencies().containsKey("supabase_syllabi_db"));
        assertTrue(dto.getDependencies().containsKey("openrouter_ai"));

        // Servicios desactivados deben marcarse como SKIPPED
        org.junit.jupiter.api.Assertions.assertEquals("SKIPPED", dto.getDependencies().get("supabase_syllabi_db").getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("SKIPPED", dto.getDependencies().get("openrouter_ai").getStatus());
    }
}
