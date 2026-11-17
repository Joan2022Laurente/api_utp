package com.utp.horario.presentation.controller;

import com.utp.horario.application.service.health.UpstreamHealthService;
import com.utp.horario.presentation.dto.UpstreamHealthDto;
import com.utp.horario.presentation.dto.UpstreamHealthDto.DependencyStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RootControllerHealthTest {

    private MockMvc mockMvc;
    private UpstreamHealthService upstreamHealthService;

    @BeforeEach
    void setUp() {
        upstreamHealthService = Mockito.mock(UpstreamHealthService.class);
        RootController rootController = new RootController(upstreamHealthService);
        mockMvc = MockMvcBuilders.standaloneSetup(rootController).build();
    }

    @Test
    @DisplayName("GET /health retorna status UP básico")
    void health_returnsSimpleUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("GET /health/upstream retorna el diagnóstico consolidado y dependencias")
    void upstreamHealth_returnsDetailedDiagnostics() throws Exception {
        UpstreamHealthDto mockReport = UpstreamHealthDto.builder()
                .status("HEALTHY")
                .summary("Todas las dependencias operan normalmente")
                .timestamp("2026-11-17T12:00:00Z")
                .totalDurationMs(340)
                .dependencies(Map.of(
                        "sso_discovery", DependencyStatus.builder()
                                .name("Keycloak SSO OpenID Discovery")
                                .status("UP")
                                .httpStatus(200)
                                .latencyMs(85L)
                                .build(),
                        "sso_jwks_certs", DependencyStatus.builder()
                                .name("Keycloak JWKS Public Keys")
                                .status("UP")
                                .httpStatus(200)
                                .latencyMs(92L)
                                .build(),
                        "portal_graphql", DependencyStatus.builder()
                                .name("Portal UTP GraphQL Gateway")
                                .status("UP")
                                .httpStatus(401)
                                .latencyMs(120L)
                                .build(),
                        "pao_gateway", DependencyStatus.builder()
                                .name("UTP+ Class PAO Gateway")
                                .status("UP")
                                .httpStatus(403)
                                .latencyMs(110L)
                                .build()
                ))
                .recommendations(List.of())
                .build();

        when(upstreamHealthService.evaluateUpstreamHealth()).thenReturn(mockReport);

        mockMvc.perform(get("/health/upstream").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("HEALTHY"))
                .andExpect(jsonPath("$.data.dependencies.sso_discovery.status").value("UP"))
                .andExpect(jsonPath("$.data.dependencies.portal_graphql.status").value("UP"))
                .andExpect(jsonPath("$.data.totalDurationMs").value(340));
    }
}
