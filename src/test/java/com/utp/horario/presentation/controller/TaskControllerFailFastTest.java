package com.utp.horario.presentation.controller;

import com.utp.horario.domain.model.AcademicActivity;
import com.utp.horario.domain.model.TaskSpecification;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerFailFastTest {

    private MockMvc mockMvc;
    private AcademicTaskServicePort taskServicePort;

    @BeforeEach
    void setUp() {
        taskServicePort = Mockito.mock(AcademicTaskServicePort.class);
        TaskController controller = new TaskController(taskServicePort);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /tasks/activities sin Authorization responde 401")
    void getActivitiesWithoutAuthResponds401() throws Exception {
        mockMvc.perform(get("/tasks/activities"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /tasks/activities con Basic auth no-Bearer responde 401")
    void getActivitiesWithBasicAuthResponds401() throws Exception {
        mockMvc.perform(get("/tasks/activities").header("Authorization", "Basic user:pass"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /tasks/activities con Bearer válido responde 200")
    void getActivitiesWithValidTokenResponds200() throws Exception {
        when(taskServicePort.getCalendarActivitiesFiltered(any(), any(), anyString(), any(), any(), any(), any()))
                .thenReturn(List.of(AcademicActivity.builder().id("act-01").title("Tarea 1").build()));

        mockMvc.perform(get("/tasks/activities")
                        .header("Authorization", "Bearer " + TestTokens.validFor("U23307609")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("GET /tasks/upcoming sin Authorization responde 401")
    void getUpcomingWithoutAuthResponds401() throws Exception {
        mockMvc.perform(get("/tasks/upcoming"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /tasks/{sectionId}/{activityId} sin Authorization responde 401")
    void getTaskDetailWithoutAuthResponds401() throws Exception {
        mockMvc.perform(get("/tasks/sec-123/act-456"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /tasks/{sectionId}/{activityId} con Bearer válido responde 200")
    void getTaskDetailWithValidTokenResponds200() throws Exception {
        when(taskServicePort.getTaskDetail(anyString(), anyString(), anyString()))
                .thenReturn(TaskSpecification.builder().id("act-456").title("Ensayo").build());

        mockMvc.perform(get("/tasks/sec-123/act-456")
                        .header("Authorization", "Bearer " + TestTokens.validFor("U23307609")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Ensayo"));
    }
}
