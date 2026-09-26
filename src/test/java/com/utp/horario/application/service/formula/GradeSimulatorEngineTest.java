package com.utp.horario.application.service.formula;

import com.utp.horario.domain.model.CourseSummaryData.CourseGradeItem;
import com.utp.horario.domain.model.CourseSummaryData.EvaluationGrade;
import com.utp.horario.domain.model.GradeSimulationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GradeSimulatorEngineTest {

    private GradeSimulatorEngine engine;

    @BeforeEach
    void setUp() {
        engine = new GradeSimulatorEngine();
    }

    @Test
    @DisplayName("Debe simular caso estándar con evaluaciones parciales y calcular promedio pendiente")
    void testStandardSimulation() {
        CourseGradeItem course = CourseGradeItem.builder()
                .courseCode("100000SI97")
                .courseName("SERVICIOS CLOUD")
                .formula("10%*[PA] + 25%*[PC1] + 25%*[PC2] + 40%*[PROY]")
                .evaluations(List.of(
                        EvaluationGrade.builder().shortName("PC1").name("Práctica 1").value("16.0").isGraded(true).build(),
                        EvaluationGrade.builder().shortName("PC2").name("Práctica 2").value("0.02").isGraded(false).build(),
                        EvaluationGrade.builder().shortName("PA").name("Participación").value("14.0").isGraded(true).build(),
                        EvaluationGrade.builder().shortName("PROY").name("Proyecto").value("0.02").isGraded(false).build()
                ))
                .build();

        // PC1 = 25% * 16 = 4.0
        // PA = 10% * 14 = 1.4
        // Total acumulado = 5.4
        // Ponderación calificada = 35%
        // Ponderación pendiente = 65%
        // Target = 12.0 -> Necesita 6.6 puntos sobre 65% -> (6.6 / 65) * 100 = 10.15
        GradeSimulationResult res = engine.simulate(course, 12.0);

        assertNotNull(res);
        assertEquals(5.4, res.getCurrentAccumulatedScore(), 0.01);
        assertEquals(35, res.getGradedWeightPercentage());
        assertEquals(65, res.getRemainingWeightPercentage());
        assertEquals(10.15, res.getRequiredAverageOnPending(), 0.05);
        assertEquals("ACHIEVABLE", res.getStatus());
        assertFalse(res.isPassed());
    }

    @Test
    @DisplayName("Debe detectar si el estudiante ya aseguró la aprobación con notas previas")
    void testAlreadyPassed() {
        CourseGradeItem course = CourseGradeItem.builder()
                .courseCode("100000SI97")
                .courseName("SERVICIOS CLOUD")
                .formula("30%*[PC1] + 40%*[PC2] + 30%*[EXFN]")
                .evaluations(List.of(
                        EvaluationGrade.builder().shortName("PC1").value("20.0").isGraded(true).build(), // 6.0
                        EvaluationGrade.builder().shortName("PC2").value("18.0").isGraded(true).build(), // 7.2
                        EvaluationGrade.builder().shortName("EXFN").value("0.02").isGraded(false).build()
                ))
                .build();

        // Acumulado: 6.0 + 7.2 = 13.2 >= 12.0
        GradeSimulationResult res = engine.simulate(course, 12.0);

        assertTrue(res.isPassed());
        assertEquals("ALREADY_PASSED", res.getStatus());
        assertEquals(0.0, res.getRequiredAverageOnPending());
    }

    @Test
    @DisplayName("Debe marcar IMPOSSIBLE si la nota requerida supera los 20 puntos")
    void testImpossibleTarget() {
        CourseGradeItem course = CourseGradeItem.builder()
                .courseCode("TEST")
                .courseName("CURSO DIFICIL")
                .formula("70%*[PC1] + 30%*[PC2]")
                .evaluations(List.of(
                        EvaluationGrade.builder().shortName("PC1").value("05.0").isGraded(true).build(), // 0.70 * 5 = 3.5
                        EvaluationGrade.builder().shortName("PC2").value("0.02").isGraded(false).build() // 30% restante
                ))
                .build();

        // Acumulado: 3.5. Meta: 12.0. Falta: 8.5 sobre 30% -> 8.5 / 0.30 = 28.33 > 20
        GradeSimulationResult res = engine.simulate(course, 12.0);

        assertFalse(res.isPassed());
        assertEquals("IMPOSSIBLE", res.getStatus());
        assertTrue(res.getRequiredAverageOnPending() > 20.0);
    }
}
