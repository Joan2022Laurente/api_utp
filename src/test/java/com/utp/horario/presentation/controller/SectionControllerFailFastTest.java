package com.utp.horario.presentation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.model.MaterialTextExtraction;
import com.utp.horario.domain.model.SectionCurriculumTree;
import com.utp.horario.domain.model.WeekAcademicContext;
import com.utp.horario.domain.port.in.AcademicTaskServicePort;
import com.utp.horario.presentation.exception.GlobalExceptionHandler;
import com.utp.horario.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyInt;
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

    @Test
    @DisplayName("GET /sections/{sectionId}/curriculum sin Authorization responde 401")
    void getCurriculumWithoutAuthResponds401() throws Exception {
        mockMvc.perform(get("/sections/seccion-dinamica-123/curriculum"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /sections/{sectionId}/curriculum con Bearer válido responde 200")
    void getCurriculumWithValidTokenResponds200() throws Exception {
        SectionCurriculumTree tree = SectionCurriculumTree.builder()
                .sectionId("seccion-dinamica-123")
                .courseCode("100000ST61")
                .numWeeks(18)
                .units(java.util.List.of())
                .build();
        when(academicTaskServicePort.getSectionCurriculum(anyString(), anyString())).thenReturn(tree);

        mockMvc.perform(get("/sections/seccion-dinamica-123/curriculum")
                        .header("Authorization", "Bearer " + TestTokens.validFor("U23307609")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.courseCode").value("100000ST61"));
    }

    @Test
    @DisplayName("GET /sections/{sectionId}/weeks/{weekNumber}/context sin Authorization responde 401")
    void getWeekContextWithoutAuthResponds401() throws Exception {
        mockMvc.perform(get("/sections/seccion-dinamica-123/weeks/7/context"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /sections/{sectionId}/weeks/{weekNumber}/context con Bearer válido responde 200")
    void getWeekContextWithValidTokenResponds200() throws Exception {
        WeekAcademicContext context = WeekAcademicContext.builder()
                .weekNumber(7)
                .sectionId("seccion-dinamica-123")
                .courseCode("100000ST61")
                .rubricAvailable(true)
                .build();
        when(academicTaskServicePort.getWeekContext(anyString(), anyInt(), anyString())).thenReturn(context);

        mockMvc.perform(get("/sections/seccion-dinamica-123/weeks/7/context")
                        .header("Authorization", "Bearer " + TestTokens.validFor("U23307609")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.weekNumber").value(7))
                .andExpect(jsonPath("$.data.rubricAvailable").value(true));
    }

    @Test
    @DisplayName("GET /sections/{sectionId}/materials/{materialId}/text sin Authorization responde 401")
    void extractMaterialTextWithoutAuthResponds401() throws Exception {
        mockMvc.perform(get("/sections/seccion-dinamica-123/materials/mat-1/text"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /sections/{sectionId}/materials/{materialId}/text con Bearer válido responde 200")
    void extractMaterialTextWithValidTokenResponds200() throws Exception {
        MaterialTextExtraction extraction = MaterialTextExtraction.builder()
                .materialId("mat-1")
                .title("Diapositivas.pdf")
                .pagesExtracted(java.util.List.of(1, 2))
                .quality("HIGH")
                .build();
        when(academicTaskServicePort.extractMaterialText(anyString(), anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(extraction);

        mockMvc.perform(get("/sections/seccion-dinamica-123/materials/mat-1/text")
                        .header("Authorization", "Bearer " + TestTokens.validFor("U23307609")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("Diapositivas.pdf"))
                .andExpect(jsonPath("$.data.quality").value("HIGH"));
    }
}
