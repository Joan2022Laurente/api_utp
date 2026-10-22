package com.utp.horario.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas del limite de intentos de autenticacion en el limite HTTP.
 */
class LoginRateLimitFilterTest {

    private RateLimitProperties properties;
    private LoginRateLimiter limiter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setMaxRequestsPerWindow(10);
        properties.setMaxFailuresPerWindow(2);
        properties.setWindowSeconds(60);
        limiter = new LoginRateLimiter(properties);
    }

    private MockMvc mockMvc() {
        LoginRateLimitFilter filter = new LoginRateLimitFilter(limiter, objectMapper);
        return MockMvcBuilders.standaloneSetup(new LoginProbeController())
                .addFilters(filter)
                .build();
    }

    @Test
    @DisplayName("Superados los intentos fallidos, el login responde 429 con el sobre ApiResponse")
    void loginIsBlockedAfterTooManyFailedAttempts() throws Exception {
        MockMvc mockMvc = mockMvc();

        mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"U99999999\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"U99999999\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"U99999999\",\"password\":\"otra\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("El limite se aplica por direccion IP")
    void theLimitIsAppliedPerIp() throws Exception {
        MockMvc mockMvc = mockMvc();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/auth/login")
                    .header("X-Forwarded-For", "203.0.113.10")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"U99999999\",\"password\":\"incorrecta\"}"));
        }

        mockMvc.perform(post("/auth/login")
                        .header("X-Forwarded-For", "203.0.113.11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"U99999999\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Las rutas que no son de login no se limitan")
    void otherRoutesAreNotLimited() throws Exception {
        MockMvc mockMvc = mockMvc();

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/otra/ruta")).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("La direccion del cliente se toma del ultimo salto de X-Forwarded-For")
    void clientIpIsTakenFromTheForwardedHeader() {
        org.springframework.mock.web.MockHttpServletRequest conSalto = new org.springframework.mock.web.MockHttpServletRequest();
        conSalto.addHeader("X-Forwarded-For", "203.0.113.10, 198.51.100.7");
        conSalto.setRemoteAddr("10.0.0.1");

        org.junit.jupiter.api.Assertions.assertEquals("198.51.100.7",
                LoginRateLimitFilter.resolveClientKey(conSalto));

        org.springframework.mock.web.MockHttpServletRequest sinCabecera = new org.springframework.mock.web.MockHttpServletRequest();
        sinCabecera.setRemoteAddr("10.0.0.1");

        org.junit.jupiter.api.Assertions.assertEquals("10.0.0.1",
                LoginRateLimitFilter.resolveClientKey(sinCabecera));
    }

    /**
     * Controlador de prueba que responde 401 como lo haria un login fallido.
     */
    @RestController
    static class LoginProbeController {
        @PostMapping("/auth/login")
        public ResponseEntity<Map<String, Object>> login() {
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }

        @PostMapping("/otra/ruta")
        public Map<String, Object> otra() {
            return Map.of("ok", true);
        }
    }
}
