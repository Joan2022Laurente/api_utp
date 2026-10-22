package com.utp.horario.infrastructure.persistence.supabase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.infrastructure.config.supabase.SupabaseProperties;
import org.junit.jupiter.api.AfterEach;
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
    private static final String TEST_COURSE_CODE = "100000TEST_UNIT";

    @BeforeEach
    void setUp() {
        SupabaseProperties properties = new SupabaseProperties();
        properties.initFallbackKeys();
        ObjectMapper objectMapper = new ObjectMapper();
        client = new SupabaseSyllabusClient(properties, objectMapper);
    }

    /**
     * Limpia el registro de prueba para no dejar artefactos en la base de datos real.
     */
    @AfterEach
    void tearDown() {
        try {
            java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
            SupabaseProperties properties = new SupabaseProperties();
            properties.initFallbackKeys();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(properties.getUrl() + "/rest/v1/" + properties.getTable()
                            + "?course_code=eq." + TEST_COURSE_CODE))
                    .header("apikey", properties.getServiceRoleKey())
                    .header("Authorization", "Bearer " + properties.getServiceRoleKey())
                    .DELETE()
                    .build();
            http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {
            // La limpieza es best effort: nunca debe hacer fallar la prueba.
        }
    }

    @Test
    @DisplayName("Debe persistir y recuperar correctamente un sílabo en Supabase PostgreSQL")
    void shouldPersistAndRetrieveSyllabusInSupabase() {
        // Prueba de integración real: requiere conectividad y credenciales de Supabase.
        org.junit.jupiter.api.Assumptions.assumeTrue(
                com.utp.horario.support.ExternalServices.isSupabaseReachable(),
                "Supabase no es alcanzable desde este entorno: se omite la prueba de integración en red.");

        String testCode = TEST_COURSE_CODE;
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
