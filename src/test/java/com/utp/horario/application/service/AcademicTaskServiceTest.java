package com.utp.horario.application.service;

import com.utp.horario.domain.model.AcademicActivity;
import com.utp.horario.domain.model.CourseSummaryData;
import com.utp.horario.domain.model.RubricCriterion;
import com.utp.horario.domain.model.TaskSpecification;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class AcademicTaskServiceTest {

    private UtpPortalGatewayPort gatewayPort;
    private com.utp.horario.application.service.formula.GradeSimulatorEngine gradeSimulatorEngine;
    private AcademicTaskService service;

    @BeforeEach
    void setUp() {
        gatewayPort = Mockito.mock(UtpPortalGatewayPort.class);
        gradeSimulatorEngine = new com.utp.horario.application.service.formula.GradeSimulatorEngine();
        service = new AcademicTaskService(gatewayPort, gradeSimulatorEngine);
    }

    @Test
    @DisplayName("Debe retornar especificación de tarea con rúbrica completa")
    void shouldReturnTaskSpecificationCorrectly() {
        TaskSpecification mockSpec = TaskSpecification.builder()
                .id("hw-123")
                .title("Avance de Portafolio 2")
                .descriptionMarkdown("### Consigna de tarea")
                .maxAttempts(1)
                .submissionTypes(List.of("online_upload", "online_url"))
                .dueAt("2026-09-28 23:59:00")
                .evaluationSystem("AVANCE DE PORTAFOLIO 2")
                .gradingRubric(List.of(
                        RubricCriterion.builder().name("Expresión oral").score(7.0).build(),
                        RubricCriterion.builder().name("Expresión corporal").score(7.0).build()
                ))
                .build();

        when(gatewayPort.fetchTaskSpecification(anyString(), anyString(), anyString())).thenReturn(mockSpec);

        TaskSpecification result = service.getTaskDetail("sec-1", "hw-123", "token");

        assertNotNull(result);
        assertEquals("hw-123", result.getId());
        assertEquals(2, result.getGradingRubric().size());
        assertEquals("AVANCE DE PORTAFOLIO 2", result.getEvaluationSystem());
    }

    @Test
    @DisplayName("Debe filtrar y ordenar próximas evaluaciones que ponderan en el promedio")
    void shouldFilterUpcomingGradedEvaluationsCorrectly() {
        LocalDateTime future1 = LocalDateTime.now().plusDays(2);
        LocalDateTime future2 = LocalDateTime.now().plusDays(5);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        AcademicActivity ev1 = AcademicActivity.builder()
                .id("ev-1")
                .title("🔴 (AC-S07-AP2) - Avance portafolio 02")
                .evaluationSystem("AVANCE DE PORTAFOLIO 2")
                .finishAt(future1.format(fmt))
                .courseName("HERRAMIENTAS PARA LA COMUNICACIÓN EFECTIVA")
                .isQualified(true)
                .build();

        AcademicActivity ev2 = AcademicActivity.builder()
                .id("ev-2")
                .title("Foro de Consultas - Semana 7")
                .evaluationSystem(null)
                .finishAt(future1.format(fmt))
                .courseName("GESTIÓN DEL SERVICIO TI")
                .isQualified(false)
                .build();

        AcademicActivity ev3 = AcademicActivity.builder()
                .id("ev-3")
                .title("Segunda Práctica Calificada (PC2)")
                .evaluationSystem("PRÁCTICA CALIFICADA 2")
                .finishAt(future2.format(fmt))
                .courseName("SERVICIOS CLOUD")
                .isQualified(true)
                .build();

        when(gatewayPort.fetchCalendarActivities(Mockito.isNull(), anyString(), anyString()))
                .thenReturn(List.of(ev1, ev2, ev3));

        List<AcademicActivity> upcoming = service.getUpcomingEvaluations("token", 5);

        assertNotNull(upcoming);
        // Debe excluir el foro porque no pondera
        assertEquals(2, upcoming.size());
        assertEquals("ev-1", upcoming.get(0).getId());
        assertEquals("ev-3", upcoming.get(1).getId());
    }

    @Test
    @DisplayName("Debe consultar resumen oficial de cursos y notas del portal")
    void shouldReturnCourseSummaryCorrectly() {
        CourseSummaryData mockSummary = CourseSummaryData.builder()
                .periodId("2263")
                .courses(List.of(
                        CourseSummaryData.CourseGradeItem.builder()
                                .courseCode("100000SI97")
                                .courseName("SERVICIOS CLOUD")
                                .formula("10%*[PA] + 25%*[PC1] + 25%*[PC2] + 40%*[PROY]")
                                .evaluations(List.of(
                                        CourseSummaryData.EvaluationGrade.builder()
                                                .name("PRACTICA CALIFICADA 1")
                                                .shortName("PC1")
                                                .value("16.00")
                                                .isGraded(true)
                                                .build()
                                ))
                                .build()
                ))
                .build();

        when(gatewayPort.fetchCourseSummary(anyString(), anyString())).thenReturn(mockSummary);

        CourseSummaryData result = service.getCourseSummary("2263", "token");

        assertNotNull(result);
        assertEquals("2263", result.getPeriodId());
        assertEquals(1, result.getCourses().size());
        assertEquals("16.00", result.getCourses().get(0).getEvaluations().get(0).getValue());
    }

    @Test
    @DisplayName("Debe simular notas requeridas en evaluaciones pendientes con fórmula oficial")
    void shouldSimulateCourseGradeCorrectly() {
        CourseSummaryData mockSummary = CourseSummaryData.builder()
                .periodId("2263")
                .courses(List.of(
                        CourseSummaryData.CourseGradeItem.builder()
                                .courseCode("100000SI97")
                                .courseName("SERVICIOS CLOUD")
                                .formula("10%*[PA] + 25%*[PC1] + 25%*[PC2] + 40%*[PROY]")
                                .evaluations(List.of(
                                        CourseSummaryData.EvaluationGrade.builder()
                                                .name("PRACTICA CALIFICADA 1")
                                                .shortName("PC1")
                                                .value("16.00")
                                                .isGraded(true)
                                                .build(),
                                        CourseSummaryData.EvaluationGrade.builder()
                                                .name("PARTICIPACION")
                                                .shortName("PA")
                                                .value("0.02")
                                                .isGraded(false)
                                                .build()
                                ))
                                .build()
                ))
                .build();

        when(gatewayPort.fetchCourseSummary(anyString(), anyString())).thenReturn(mockSummary);

        com.utp.horario.domain.model.GradeSimulationResult sim = service.simulateCourseGrade("100000SI97", 12.0, "2263", "token");

        assertNotNull(sim);
        assertEquals("100000SI97", sim.getCourseCode());
        // PC1 es 25% con 16 -> 4.0 puntos acumulados
        assertEquals(4.0, sim.getCurrentAccumulatedScore(), 0.01);
        assertEquals(25, sim.getGradedWeightPercentage());
        assertEquals(75, sim.getRemainingWeightPercentage());
        // Meta 12.0, faltan 8.0 puntos sobre 75% -> 8 / 0.75 = 10.67
        assertNotNull(sim.getRequiredAverageOnPending());
        assertEquals(10.67, sim.getRequiredAverageOnPending(), 0.05);
        assertEquals("ACHIEVABLE", sim.getStatus());
        assertFalse(sim.isPassed());
    }
}
