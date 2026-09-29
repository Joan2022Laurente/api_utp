package com.utp.horario.infrastructure.persistence.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.infrastructure.config.supabase.SupabaseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupabaseSyllabusClientTest {

    private SupabaseSyllabusClient client;

    @BeforeEach
    void setUp() {
        SupabaseProperties properties = new SupabaseProperties();
        properties.initFallbackKeys();
        ObjectMapper objectMapper = new ObjectMapper();
        client = new SupabaseSyllabusClient(properties, objectMapper);
    }

    @Test
    @DisplayName("Debe persistir y recuperar correctamente un sílabo en Supabase PostgreSQL")
    void shouldPersistAndRetrieveSyllabusInSupabase() {
        String testCode = "100000TEST_UNIT";
        Syllabus sample = Syllabus.builder()
                .id(testCode)
                .courseCode(testCode)
                .courseName("CURSO DE PRUEBA UNITARIA")
                .credits(4)
                .weeklyHours(5)
                .modality("Presencial")
                .formula("(0.3*PC1) + (0.7*EF)")
                .learningGoal("Objetivo de aprendizaje del curso de prueba")
                .evaluations(List.of(
                        com.utp.horario.domain.model.SyllabusEvaluation.builder()
                                .type("PC1")
                                .description("Práctica Calificada 1")
                                .weightPercent(30)
                                .week(5)
                                .build()
                ))
                .weeklySchedule(List.of(
                        com.utp.horario.domain.model.SyllabusWeeklySession.builder()
                                .week(1)
                                .unit("Unidad 1")
                                .topic("Introducción y conceptos")
                                .activities("Laboratorio práctico 1")
                                .build()
                ))
                .build();

        client.upsert(sample);

        Optional<Syllabus> found = client.findByCourseCode(testCode);
        assertTrue(found.isPresent(), "El sílabo recién guardado debe existir en Supabase");
        Syllabus s = found.get();
        assertEquals(testCode, s.getCourseCode());
        assertEquals("CURSO DE PRUEBA UNITARIA", s.getCourseName());
        assertEquals("(0.3*PC1) + (0.7*EF)", s.getFormula());
        assertNotNull(s.getWeeklySchedule());
        assertFalse(s.getWeeklySchedule().isEmpty());
        assertNotNull(s.getEvaluations());
        assertFalse(s.getEvaluations().isEmpty());

        List<Syllabus> all = client.findAll();
        assertNotNull(all);
        assertTrue(all.stream().anyMatch(item -> testCode.equals(item.getCourseCode())));
    }
}
