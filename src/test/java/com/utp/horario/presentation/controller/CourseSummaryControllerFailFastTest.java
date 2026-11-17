package com.utp.horario.presentation.controller;

import com.utp.horario.domain.model.CourseSummaryData;
import com.utp.horario.domain.model.GradeSimulationResult;
import com.utp.horario.domain.port.in.AcademicTaskServicePort;
import com.utp.horario.presentation.exception.GlobalExceptionHandler;
import com.utp.horario.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseSummaryControllerFailFastTest {

    private MockMvc mockMvc;
    private AcademicTaskServicePort taskServicePort;

    @BeforeEach
    void setUp() {
        taskServicePort = Mockito.mock(AcademicTaskServicePort.class);
        CourseSummaryController controller = new CourseSummaryController(taskServicePort);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /courses/summary sin Authorization responde 401")
    void getSummaryWithoutAuthResponds401() throws Exception {
        mockMvc.perform(get("/courses/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /courses/summary con Basic auth no-Bearer responde 401")
    void getSummaryWithBasicAuthResponds401() throws Exception {
        mockMvc.perform(get("/courses/summary").header("Authorization", "Basic user:pass"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /courses/summary con Bearer válido responde 200")
    void getSummaryWithValidTokenResponds200() throws Exception {
        when(taskServicePort.getCourseSummary(anyString(), anyString()))
                .thenReturn(CourseSummaryData.builder().periodId("2263").courses(List.of()).build());

        mockMvc.perform(get("/courses/summary")
                        .header("Authorization", "Bearer " + TestTokens.validFor("U23307609")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /courses/simulator sin Authorization responde 401")
    void simulateAllWithoutAuthResponds401() throws Exception {
        mockMvc.perform(get("/courses/simulator"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /courses/{courseCode}/simulator sin Authorization responde 401")
    void simulateCourseWithoutAuthResponds401() throws Exception {
        mockMvc.perform(get("/courses/100000ST61/simulator"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /courses/{courseCode}/simulator con Bearer válido responde 200")
    void simulateCourseWithValidTokenResponds200() throws Exception {
        when(taskServicePort.simulateCourseGrade(anyString(), anyDouble(), anyString(), anyString()))
                .thenReturn(GradeSimulationResult.builder().courseCode("100000ST61").build());

        mockMvc.perform(get("/courses/100000ST61/simulator")
                        .header("Authorization", "Bearer " + TestTokens.validFor("U23307609")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
