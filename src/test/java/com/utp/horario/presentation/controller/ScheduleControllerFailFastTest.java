package com.utp.horario.presentation.controller;

import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.port.in.ScheduleServicePort;
import com.utp.horario.presentation.exception.GlobalExceptionHandler;
import com.utp.horario.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScheduleControllerFailFastTest {

    private MockMvc mockMvc;
    private ScheduleServicePort scheduleServicePort;

    @BeforeEach
    void setUp() {
        scheduleServicePort = Mockito.mock(ScheduleServicePort.class);
        ScheduleController controller = new ScheduleController(scheduleServicePort);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /schedule sin cabecera Authorization responde 401 Unauthorized")
    void getScheduleWithoutAuthResponds401() throws Exception {
        mockMvc.perform(get("/schedule"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Se requiere token de sesión institucional UTP con formato 'Bearer <token>'"));
    }

    @Test
    @DisplayName("GET /schedule con cabecera no-Bearer (ej. Basic) responde 401 Unauthorized")
    void getScheduleWithNonBearerHeaderResponds401() throws Exception {
        mockMvc.perform(get("/schedule").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /schedule con token Bearer inválido responde 401 Unauthorized")
    void getScheduleWithInvalidTokenResponds401() throws Exception {
        mockMvc.perform(get("/schedule").header("Authorization", "Bearer token-invalido-sin-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /schedule con token Bearer válido responde 200 OK")
    void getScheduleWithValidTokenResponds200() throws Exception {
        when(scheduleServicePort.getStudentSchedule(anyString(), anyString(), anyString()))
                .thenReturn(ScheduleInterval.builder().id("interval-ok").build());

        mockMvc.perform(get("/schedule").header("Authorization", "Bearer " + TestTokens.validFor("U23307609")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("interval-ok"));
    }

    @Test
    @DisplayName("POST /schedule/sync sin token responde 401 Unauthorized")
    void syncScheduleWithoutTokenResponds401() throws Exception {
        mockMvc.perform(post("/schedule/sync"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /schedule/sync con query ?token= inválido responde 401 Unauthorized")
    void syncScheduleWithInvalidQueryTokenResponds401() throws Exception {
        mockMvc.perform(post("/schedule/sync").param("token", "string-aleatorio-no-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /schedule/sync con Bearer token válido responde 200 OK")
    void syncScheduleWithValidTokenResponds200() throws Exception {
        when(scheduleServicePort.syncScheduleFromUtp(anyString(), anyString()))
                .thenReturn(ScheduleInterval.builder().id("interval-sync").build());

        mockMvc.perform(post("/schedule/sync")
                        .header("Authorization", "Bearer " + TestTokens.validFor("U23307609")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("interval-sync"));
    }

    @Test
    @DisplayName("GET /schedule/export.ics sin token responde 401 Unauthorized y no devuelve ICS 200 vacío")
    void exportScheduleIcsWithoutTokenResponds401() throws Exception {
        mockMvc.perform(get("/schedule/export.ics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /schedule/export.ics con Bearer token válido responde 200 y archivo text/calendar")
    void exportScheduleIcsWithValidTokenResponds200() throws Exception {
        when(scheduleServicePort.exportScheduleToIcs(anyString(), anyString(), anyString()))
                .thenReturn("BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR");

        mockMvc.perform(get("/schedule/export.ics")
                        .header("Authorization", "Bearer " + TestTokens.validFor("U23307609")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/calendar; charset=utf-8"));
    }
}
