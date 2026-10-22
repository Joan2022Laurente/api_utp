package com.utp.horario.application.usecase;

import com.utp.horario.application.service.SyllabusParserEngine;
import com.utp.horario.application.service.ai.OpenRouterFleetService;
import com.utp.horario.application.service.ai.SyllabusPdfToMarkdownSanitizer;
import com.utp.horario.application.service.export.SyllabusMarkdownExporter;
import com.utp.horario.application.service.validation.SyllabusDeterministicValidator;
import com.utp.horario.domain.model.CourseSummaryData;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import com.utp.horario.domain.port.out.SyllabusRepositoryPort;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de estabilidad del pipeline de extraccion de silabos.
 *
 * <p>Bloquean dos regresiones:</p>
 * <ol>
 *   <li>El resultado de la IA debe devolverse <b>sin mutaciones</b>: las apps cliente
 *       dependen del JSON que genera la IA cuando este supera el Quality Gate.</li>
 *   <li>El Portal UTP debe actuar como <b>fallback</b> (solo cuando IA y motor determinista
 *       fallan), nunca como mutador del resultado de la IA.</li>
 * </ol>
 */
class SyllabusServiceImplFallbackTest {

    private SyllabusRepositoryPort repository;
    private UtpPortalGatewayPort gateway;
    private OpenRouterFleetService fleet;
    private SyllabusParserEngine parserEngine;
    private SyllabusServiceImpl service;

    private static final String TOKEN = "fake.jwt.token";
    private static final String COURSE = "100000SI82";

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(SyllabusRepositoryPort.class);
        gateway = Mockito.mock(UtpPortalGatewayPort.class);
        fleet = Mockito.mock(OpenRouterFleetService.class);
        parserEngine = Mockito.mock(SyllabusParserEngine.class);

        when(repository.findByCourseCode(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(Syllabus.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gateway.fetchSyllabusPdfText(anyString(), anyString())).thenReturn("SÍLABO ... contenido ...");

        service = new SyllabusServiceImpl(repository, gateway, parserEngine, fleet,
                new SyllabusMarkdownExporter(), new SyllabusPdfToMarkdownSanitizer(),
                new SyllabusDeterministicValidator());
    }

    private Syllabus validAiSyllabus() {
        return Syllabus.builder()
                .id(COURSE).courseCode(COURSE).courseName("CURSO IA")
                .formula("(40%)PC1 + (60%)PROY")
                .evaluations(List.of(
                        SyllabusEvaluation.builder().id("PC1").type("PC1").description("Practica 1")
                                .week(4).weightPercent(40).build(),
                        SyllabusEvaluation.builder().id("PROY").type("PROY").description("Proyecto")
                                .week(18).weightPercent(60).build()))
                .weeklySchedule(List.of(
                        SyllabusWeeklySession.builder().week(1).unit("Unidad 1").topic("Tema 1").build(),
                        SyllabusWeeklySession.builder().week(2).unit("Unidad 1").topic("Tema 2").build(),
                        SyllabusWeeklySession.builder().week(3).unit("Unidad 1").topic("Tema 3").build(),
                        SyllabusWeeklySession.builder().week(4).unit("Unidad 1").topic("Tema 4").build()))
                .build();
    }

    @Test
    @DisplayName("El JSON de la IA se devuelve intacto: el Portal NO se consulta en el camino feliz")
    void aiResultIsReturnedUntouched() {
        Syllabus ai = validAiSyllabus();
        when(fleet.parseSyllabusWithAi(anyString(), anyString())).thenReturn(Optional.of(ai));

        Syllabus result = service.getSyllabus(COURSE, null, null, TOKEN);

        assertNotNull(result);
        assertEquals("(40%)PC1 + (60%)PROY", result.getFormula(), "La formula de la IA no debe mutarse");
        assertEquals(40, result.getEvaluations().get(0).getWeightPercent());
        assertEquals(60, result.getEvaluations().get(1).getWeightPercent());

        // El Portal no debe intervenir cuando la IA es valida.
        verify(gateway, never()).fetchCourseSummary(anyString(), anyString());
    }

    @Test
    @DisplayName("Si la IA falla, gana el motor determinista (sin intervencion del Portal)")
    void deterministicFallbackWinsOverPortal() {
        when(fleet.parseSyllabusWithAi(anyString(), anyString())).thenReturn(Optional.empty());

        Syllabus deterministic = validAiSyllabus().toBuilder().courseName("CURSO DETERMINISTA").build();
        when(parserEngine.parse(anyString(), anyString())).thenReturn(deterministic);

        Syllabus result = service.getSyllabus(COURSE, null, null, TOKEN);

        assertNotNull(result);
        assertEquals("CURSO DETERMINISTA", result.getCourseName());
        verify(gateway, never()).fetchCourseSummary(anyString(), anyString());
    }

    @Test
    @DisplayName("Si IA y determinista fallan, el Portal UTP aporta el sistema de evaluaciones oficial")
    void officialPortalActsAsLastResortFallback() {
        when(fleet.parseSyllabusWithAi(anyString(), anyString())).thenReturn(Optional.empty());

        // El motor determinista produce un sílabo que NO supera el Quality Gate.
        Syllabus invalid = Syllabus.builder()
                .id(COURSE).courseCode(COURSE).courseName("CURSO DEGRADADO")
                .formula("(70%)PC1")
                .evaluations(List.of(SyllabusEvaluation.builder().id("PC1").type("PC1")
                        .description("Practica 1").week(4).weightPercent(70).build()))
                .weeklySchedule(List.of(
                        SyllabusWeeklySession.builder().week(1).unit("U1").topic("T1").build(),
                        SyllabusWeeklySession.builder().week(2).unit("U1").topic("T2").build(),
                        SyllabusWeeklySession.builder().week(3).unit("U1").topic("T3").build(),
                        SyllabusWeeklySession.builder().week(4).unit("U1").topic("T4").build()))
                .build();
        when(parserEngine.parse(anyString(), anyString())).thenReturn(invalid);

        CourseSummaryData summary = CourseSummaryData.builder()
                .periodId("2263")
                .courses(List.of(CourseSummaryData.CourseGradeItem.builder()
                        .courseCode(COURSE)
                        .courseName("CURSO OFICIAL")
                        .section("56357")
                        .formula("10%*[ATI1] + 20%*[ATI2] + 20%*[ATI3] + 20%*[PA] + 30%*[TI]")
                        .evaluations(List.of(
                                CourseSummaryData.EvaluationGrade.builder().shortName("ATI1").name("AVANCE 1").build(),
                                CourseSummaryData.EvaluationGrade.builder().shortName("ATI2").name("AVANCE 2").build(),
                                CourseSummaryData.EvaluationGrade.builder().shortName("ATI3").name("AVANCE 3").build(),
                                CourseSummaryData.EvaluationGrade.builder().shortName("PA").name("PARTICIPACION").build(),
                                CourseSummaryData.EvaluationGrade.builder().shortName("TI").name("TRABAJO").build()))
                        .build()))
                .build();
        when(gateway.fetchCourseSummary(anyString(), anyString())).thenReturn(summary);

        Syllabus result = service.getSyllabus(COURSE, null, null, TOKEN);

        assertNotNull(result);
        assertEquals(5, result.getEvaluations().size(), "Debe usar el sistema de evaluaciones oficial");
        assertEquals(10, result.getEvaluations().get(0).getWeightPercent());
        assertEquals(30, result.getEvaluations().get(4).getWeightPercent());
        assertEquals("(10%)ATI1 + (20%)ATI2 + (20%)ATI3 + (20%)PA + (30%)TI", result.getFormula());
        assertEquals(4, result.getWeeklySchedule().size(), "Conserva el cronograma determinista");
    }

    @Test
    @DisplayName("Si el Portal no esta disponible, se conserva el resultado determinista sin fallar")
    void portalUnavailableKeepsDeterministicResult() {
        when(fleet.parseSyllabusWithAi(anyString(), anyString())).thenReturn(Optional.empty());

        Syllabus invalid = Syllabus.builder()
                .id(COURSE).courseCode(COURSE).courseName("CURSO DEGRADADO")
                .formula("(70%)PC1")
                .evaluations(List.of(SyllabusEvaluation.builder().id("PC1").type("PC1")
                        .description("Practica 1").week(4).weightPercent(70).build()))
                .weeklySchedule(List.of(
                        SyllabusWeeklySession.builder().week(1).unit("U1").topic("T1").build(),
                        SyllabusWeeklySession.builder().week(2).unit("U1").topic("T2").build(),
                        SyllabusWeeklySession.builder().week(3).unit("U1").topic("T3").build(),
                        SyllabusWeeklySession.builder().week(4).unit("U1").topic("T4").build()))
                .build();
        when(parserEngine.parse(anyString(), anyString())).thenReturn(invalid);
        when(gateway.fetchCourseSummary(anyString(), anyString())).thenThrow(new RuntimeException("Portal no disponible"));

        Syllabus result = service.getSyllabus(COURSE, null, null, TOKEN);

        assertNotNull(result, "Nunca debe propagating una excepcion por un fallback opcional");
        assertEquals("CURSO DEGRADADO", result.getCourseName());
    }

    @Test
    @DisplayName("Sin token no se consulta el Portal (fallback oficial indisponible)")
    void officialFallbackRequiresToken() {
        when(fleet.parseSyllabusWithAi(anyString(), anyString())).thenReturn(Optional.empty());
        Syllabus invalid = Syllabus.builder()
                .id(COURSE).courseCode(COURSE).courseName("DEGRADADO")
                .formula("(70%)PC1")
                .evaluations(List.of())
                .weeklySchedule(List.of())
                .build();
        when(parserEngine.parse(anyString(), anyString())).thenReturn(invalid);

        Syllabus result = service.getSyllabus(COURSE, null, null, null);

        assertNotNull(result);
        verify(gateway, never()).fetchCourseSummary(anyString(), anyString());
        assertEquals("(70%)PC1", result.getFormula(), "Sin token no se sustituye el resultado determinista");
        assertEquals(0, result.getEvaluations().size());
    }
}
