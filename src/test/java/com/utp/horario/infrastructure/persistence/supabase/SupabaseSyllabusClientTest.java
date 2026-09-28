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
    @DisplayName("Debe consultar y mapear correctamente sílabos existentes en Supabase PostgreSQL")
    void shouldFindExistingSyllabusInSupabase() {
        Optional<Syllabus> syllabus = client.findByCourseCode("100000SI97");

        assertTrue(syllabus.isPresent(), "El curso 100000SI97 debe existir en Supabase");
        Syllabus s = syllabus.get();
        assertEquals("100000SI97", s.getCourseCode());
        assertEquals("SERVICIOS CLOUD", s.getCourseName());
        assertNotNull(s.getFormula());
        assertNotNull(s.getWeeklySchedule());
        assertFalse(s.getWeeklySchedule().isEmpty(), "Debe contener el cronograma semanal");
        assertNotNull(s.getEvaluations());
        assertFalse(s.getEvaluations().isEmpty(), "Debe contener las evaluaciones");
    }

    @Test
    @DisplayName("Debe listar todos los sílabos almacenados en Supabase")
    void shouldListAllSyllabi() {
        List<Syllabus> all = client.findAll();
        assertNotNull(all);
        assertTrue(all.size() >= 6, "Debe tener al menos los 6 sílabos oficiales cargados");
    }
}
