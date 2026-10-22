package com.utp.horario.presentation.controller;

import com.utp.horario.domain.model.Course;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.port.in.ScheduleServicePort;
import com.utp.horario.domain.port.in.SyllabusServicePort;
import com.utp.horario.presentation.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

/**
 * Pruebas del catalogo de silabos.
 *
 * <p>La garantia importante es que el comportamiento por defecto <b>no cambia</b>: el
 * catalogo completo se sigue devolviendo tal cual. El filtrado por alumno es una via
 * optativa ({@code scope=student}) que las apps existentes no usan.</p>
 */
class SyllabusCatalogControllerTest {

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

        when(syllabusServicePort.getAllSyllabiForStudent(Mockito.anyString()))
                .thenReturn(List.of(
                        Syllabus.builder().id("1").courseCode("100000ST61").courseName("DESARROLLO WEB INTEGRADO").build(),
                        Syllabus.builder().id("2").courseCode("100000SI82").courseName("FORMACION PARA LA INVESTIGACION").build()));
    }

    @Test
    @DisplayName("Por defecto se devuelve el catalogo completo (comportamiento historico)")
    void defaultReturnsTheWholeCatalog() throws Exception {
        mockMvc.perform(get("/syllabus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].courseCode").value("100000ST61"));
    }

    @Test
    @DisplayName("Con scope=student se devuelven solo los cursos matriculados")
    void studentScopeFiltersByEnrolledCourses() throws Exception {
        when(scheduleServicePort.getStudentSchedule(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(ScheduleInterval.builder()
                        .courses(List.of(Course.builder().code("100000SI82").name("FORMACION PARA LA INVESTIGACION").build()))
                        .build());

        mockMvc.perform(get("/syllabus").param("scope", "student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].courseCode").value("100000SI82"));
    }

    @Test
    @DisplayName("Con scope=student y sin cursos matriculados se devuelve una lista vacia")
    void studentScopeWithoutCoursesReturnsEmptyList() throws Exception {
        when(scheduleServicePort.getStudentSchedule(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
                .thenReturn(ScheduleInterval.builder().courses(List.of()).build());

        mockMvc.perform(get("/syllabus").param("scope", "student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
