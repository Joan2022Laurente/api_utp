package com.utp.horario.presentation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.port.in.AcademicTaskServicePort;
import com.utp.horario.presentation.exception.GlobalExceptionHandler;
import com.utp.horario.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fail-fast del endpoint del árbol completo de la sección. */
class SectionControllerFailFastTest {

    private MockMvc mockMvc;
    private AcademicTaskServicePort academicTaskServicePort;

    @BeforeEach
    void setUp() {
        academicTaskServicePort = Mockito.mock(AcademicTaskServicePort.class);
        SectionController controller = new SectionController(academicTaskServicePort);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /sections/{sectionId}/full sin Authorization responde 401")
    void getSectionFullWithoutAuthResponds401() throws Exception {
        mockMvc.perform(get("/sections/seccion-dinamica-123/full"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /sections/{sectionId}/full con Basic auth no-Bearer responde 401")
    void getSectionFullWithBasicAuthResponds401() throws Exception {
        mockMvc.perform(get("/sections/seccion-dinamica-123/full")
                        .header("Authorization", "Basic user:pass"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /sections/{sectionId}/full con Bearer válido responde 200 con el árbol")
    void getSectionFullWithValidTokenResponds200() throws Exception {
        JsonNode tree = new ObjectMapper().readTree("""
                {"unities":[{"id":"u1","themes":[{"weekNumber":3,"contents":[]}]}]}
                """);
        when(academicTaskServicePort.getSectionFull(anyString(), anyString())).thenReturn(tree);

        mockMvc.perform(get("/sections/seccion-dinamica-123/full")
                        .header("Authorization", "Bearer " + TestTokens.validFor("U23307609")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unities[0].themes[0].weekNumber").value(3));
    }
}
