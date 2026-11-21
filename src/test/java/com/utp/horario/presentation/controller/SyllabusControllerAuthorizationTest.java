package com.utp.horario.presentation.controller;

import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.port.in.ScheduleServicePort;
import com.utp.horario.domain.port.in.SyllabusServicePort;
import com.utp.horario.presentation.exception.GlobalExceptionHandler;
import com.utp.horario.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de la politica de autorizacion opcional de los endpoints de silabo.
 *
 * <p>Regla: la ausencia de {@code Authorization} sigue permitiendo el acceso de solo
 * cache, pero una cabecera presente y malformada (por ejemplo {@code Basic ...} o un
 * Bearer que no es un JWT de tres segmentos) debe rechazarse con 401 y nunca llegar al
 * caso de uso.</p>
 */
class SyllabusControllerAuthorizationTest {

    private MockMvc mockMvc;
    private SyllabusServicePort syllabusServicePort;
    private ScheduleServicePort scheduleServicePort;

    @BeforeEach
    void setUp() {
        syllabusServicePort = Mockito.mock(SyllabusServicePort.class);
        scheduleServicePort = Mockito.mock(ScheduleServicePort.class);
        SyllabusController controller = new SyllabusController(syllabusServicePort, scheduleServicePort);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        when(syllabusServicePort.getSyllabus(any(), any(), any(), any()))
                .thenReturn(Syllabus.builder()
                        .id("1")
                        .courseCode("100000ST61")
                        .courseName("DESARROLLO WEB INTEGRADO")
                        .build());
    }

    @Test
    @DisplayName("Sin cabecera Authorization se permite el acceso a cache y el token llega nulo")
    void absentAuthorizationAllowsCachedOnlyAccess() throws Exception {
        mockMvc.perform(get("/syllabus/{courseCode}", "100000ST61"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(syllabusServicePort).getSyllabus(eq("100000ST61"), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("Una cabecera Basic se rechaza con 401 y no se invoca el caso de uso")
    void basicAuthorizationIsRejected() throws Exception {
        mockMvc.perform(get("/syllabus/{courseCode}", "100000ST61")
                        .header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(syllabusServicePort, never()).getSyllabus(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Un Bearer con JWT de dos segmentos se rechaza con 401")
    void bearerWithMalformedJwtIsRejected() throws Exception {
        String[] parts = TestTokens.validFor("U12345678").split("\\.");
        String twoSegments = parts[0] + "." + parts[1];

        mockMvc.perform(get("/syllabus/{courseCode}", "100000ST61")
                        .header("Authorization", "Bearer " + twoSegments))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        verify(syllabusServicePort, never()).getSyllabus(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Un Bearer vigente se acepta y se propaga al caso de uso")
    void validBearerIsPropagated() throws Exception {
        String token = TestTokens.validFor("U12345678");

        mockMvc.perform(get("/syllabus/{courseCode}", "100000ST61")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(syllabusServicePort).getSyllabus(eq("100000ST61"), isNull(), isNull(), eq(token));
    }

    @Test
    @DisplayName("El catalogo con scope=student tambien rechaza cabeceras que no son Bearer JWT")
    void catalogStudentScopeRejectsNonBearerAuthorization() throws Exception {
        when(syllabusServicePort.getAllSyllabiForStudent(Mockito.anyString()))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/syllabus")
                        .param("scope", "student")
                        .header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("El alias /syllabuses/{courseCode} responde 200 igual que /syllabus")
    void aliasSyllabusesResponds200() throws Exception {
        mockMvc.perform(get("/syllabuses/{courseCode}", "100000ST61"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /syllabus/raw-text con courseCode en blanco responde 400 Bad Request")
    void rawTextWithBlankCourseCodeResponds400() throws Exception {
        mockMvc.perform(get("/syllabus/raw-text").param("courseCode", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
