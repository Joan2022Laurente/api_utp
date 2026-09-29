package com.utp.horario.application.service.validation;

import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyllabusDeterministicValidatorTest {

    private SyllabusDeterministicValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SyllabusDeterministicValidator();
    }

    private Syllabus.SyllabusBuilder createValidBase() {
        List<SyllabusEvaluation> evals = List.of(
                SyllabusEvaluation.builder().type("PC1").description("Práctica 1").weightPercent(20).week(4).build(),
                SyllabusEvaluation.builder().type("PC2").description("Práctica 2").weightPercent(30).week(10).build(),
                SyllabusEvaluation.builder().type("EF").description("Examen Final").weightPercent(50).week(18).build()
        );

        List<SyllabusWeeklySession> schedule = new ArrayList<>();
        for (int w = 1; w <= 18; w++) {
            schedule.add(SyllabusWeeklySession.builder()
                    .week(w)
                    .unit("Unidad " + ((w - 1) / 6 + 1))
                    .topic("Tema de la semana " + w + " sobre conceptos sólidos")
                    .activities("Actividad práctica y laboratorio " + w)
                    .build());
        }

        return Syllabus.builder()
                .id("100000TEST")
                .courseCode("100000TEST")
                .courseName("ARQUITECTURA DE SOFTWARE DISTRIBUIDO")
                .credits(4)
                .modality("Presencial")
                .formula("(0.20*PC1) + (0.30*PC2) + (0.50*EF)")
                .learningGoal("El estudiante diseña sistemas de alta concurrencia y tolerancia a fallos.")
                .evaluations(evals)
                .weeklySchedule(schedule);
    }

    @Test
    @DisplayName("Debe aceptar un sílabo académico válido y consistente de 18 semanas")
    void shouldAcceptValidSyllabus() {
        Syllabus syllabus = createValidBase().build();
        SyllabusDeterministicValidator.ValidationResult result = validator.validate(syllabus);

        assertTrue(result.isValid(), "Un sílabo completo y coherente debe ser válido: " + result.getViolations());
    }

    @Test
    @DisplayName("Debe aceptar sílabos de Ciclo de Verano (8 semanas) sin forzar 18 semanas")
    void shouldAcceptSummerCycleCourse() {
        List<SyllabusEvaluation> summerEvals = List.of(
                SyllabusEvaluation.builder().type("PC1").description("Práctica Calificada 1").weightPercent(30).week(3).build(),
                SyllabusEvaluation.builder().type("PC2").description("Práctica Calificada 2").weightPercent(30).week(6).build(),
                SyllabusEvaluation.builder().type("EF").description("Examen Final").weightPercent(40).week(8).build()
        );

        List<SyllabusWeeklySession> summerSchedule = new ArrayList<>();
        for (int w = 1; w <= 8; w++) {
            summerSchedule.add(SyllabusWeeklySession.builder()
                    .week(w)
                    .unit("Unidad " + ((w - 1) / 2 + 1))
                    .topic("Tema de verano intensivo semana " + w)
                    .activities("Laboratorio acelerado semana " + w)
                    .build());
        }

        Syllabus summerSyllabus = createValidBase()
                .id("100000VERANO")
                .courseCode("100000VERANO")
                .courseName("ALGORITMOS AVANZADOS - CICLO VERANO")
                .formula("(0.30*PC1) + (0.30*PC2) + (0.40*EF)")
                .evaluations(summerEvals)
                .weeklySchedule(summerSchedule)
                .build();

        SyllabusDeterministicValidator.ValidationResult result = validator.validate(summerSyllabus);
        assertTrue(result.isValid(), "Un curso de ciclo de verano (8 semanas) debe ser aceptado sin sesgo: " + result.getViolations());
    }

    @Test
    @DisplayName("Debe aceptar cursos modulares intensivos (6 semanas)")
    void shouldAcceptModularCourse() {
        List<SyllabusEvaluation> modularEvals = List.of(
                SyllabusEvaluation.builder().type("EC1").description("Evaluación Continua 1").weightPercent(40).week(3).build(),
                SyllabusEvaluation.builder().type("EF").description("Evaluación Final").weightPercent(60).week(6).build()
        );

        List<SyllabusWeeklySession> modularSchedule = new ArrayList<>();
        for (int w = 1; w <= 6; w++) {
            modularSchedule.add(SyllabusWeeklySession.builder()
                    .week(w)
                    .unit("Módulo " + w)
                    .topic("Taller intensivo sesión " + w)
                    .activities("Práctica aplicada " + w)
                    .build());
        }

        Syllabus modularSyllabus = createValidBase()
                .id("100000MODULAR")
                .courseCode("100000MODULAR")
                .courseName("TALLER DE ESPECIALIZACIÓN MODULAR")
                .formula("(0.40*EC1) + (0.60*EF)")
                .evaluations(modularEvals)
                .weeklySchedule(modularSchedule)
                .build();

        SyllabusDeterministicValidator.ValidationResult result = validator.validate(modularSyllabus);
        assertTrue(result.isValid(), "Un curso modular (6 semanas) debe ser aceptado: " + result.getViolations());
    }

    @Test
    @DisplayName("Debe rechazar si la suma de porcentajes de evaluación no es ~100%")
    void shouldRejectInvalidEvaluationWeights() {
        // Pesos suman 70% (20 + 20 + 30)
        List<SyllabusEvaluation> badEvals = List.of(
                SyllabusEvaluation.builder().type("PC1").weightPercent(20).week(4).build(),
                SyllabusEvaluation.builder().type("PC2").weightPercent(20).week(10).build(),
                SyllabusEvaluation.builder().type("EF").weightPercent(30).week(18).build()
        );

        Syllabus syllabus = createValidBase().evaluations(badEvals).build();
        SyllabusDeterministicValidator.ValidationResult result = validator.validate(syllabus);

        assertFalse(result.isValid(), "Debe rechazar cuando la suma de pesos no es 100%");
        assertTrue(result.getViolations().stream().anyMatch(v -> v.contains("suma de pesos")), 
                "Debe especificar error en la suma de pesos");
    }

    @Test
    @DisplayName("Debe rechazar semanas duplicadas o fuera de rango [1-18]")
    void shouldRejectInvalidWeeksInSchedule() {
        List<SyllabusWeeklySession> badSchedule = new ArrayList<>();
        // Semana 1 repetida dos veces y una semana 25 fuera de rango
        badSchedule.add(SyllabusWeeklySession.builder().week(1).topic("Tema 1").build());
        badSchedule.add(SyllabusWeeklySession.builder().week(1).topic("Tema 1 duplicado").build());
        for (int w = 2; w <= 8; w++) {
            badSchedule.add(SyllabusWeeklySession.builder().week(w).topic("Tema " + w).build());
        }
        badSchedule.add(SyllabusWeeklySession.builder().week(25).topic("Semana 25 fuera de semestre").build());

        Syllabus syllabus = createValidBase().weeklySchedule(badSchedule).build();
        SyllabusDeterministicValidator.ValidationResult result = validator.validate(syllabus);

        assertFalse(result.isValid(), "Debe rechazar semanas duplicadas o > 18");
        assertTrue(result.getViolations().stream().anyMatch(v -> v.contains("duplicada") || v.contains("rango")));
    }

    @Test
    @DisplayName("Debe rechazar campos con cadenas basura o placeholders (N/A, null, undefined)")
    void shouldRejectGarbageStrings() {
        Syllabus syllabus = createValidBase()
                .courseName("N/A")
                .formula("null")
                .build();

        SyllabusDeterministicValidator.ValidationResult result = validator.validate(syllabus);
        assertFalse(result.isValid(), "Debe rechazar nombres o fórmulas basura");
    }

    @Test
    @DisplayName("Debe detectar marcas de alucinación típicas de LLM")
    void shouldRejectHallucinationMarkers() {
        Syllabus syllabus = createValidBase()
                .learningGoal("As an AI language model, I do not have access to real goals.")
                .build();

        SyllabusDeterministicValidator.ValidationResult result = validator.validate(syllabus);
        assertFalse(result.isValid(), "Debe detectar marcas de alucinación");
        assertTrue(result.getViolations().stream().anyMatch(v -> v.contains("alucinación")));
    }
}
