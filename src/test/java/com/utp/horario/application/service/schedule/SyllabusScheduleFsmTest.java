package com.utp.horario.application.service.schedule;

import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyllabusScheduleFsmTest {

    @Test
    @DisplayName("Debe construir cronograma de 18 semanas para curso de 36 sesiones y mapear evaluaciones correctamente")
    void shouldBuild18WeeksFrom36SessionsAndMapEvaluations() {
        String input = """
                8. CRONOGRAMA DE ACTIVIDADES
                1 1 Introducción a Cloud Computing
                1 2 Arquitectura y Componentes
                3 5 Almacenamiento S3 y Bloques
                3 6 Base de Datos NoSQL
                12 23 Scheduling y Recursos
                12 24 Evaluación PC2
                18 35 Sustentación
                18 36 Evaluación PROYECTO FINAL
                """;

        List<SyllabusEvaluation> evals = List.of(
                SyllabusEvaluation.builder().type("PC1").week(3).weightPercent(25).build(),
                SyllabusEvaluation.builder().type("PC2").week(12).weightPercent(25).build(),
                SyllabusEvaluation.builder().type("PROY").week(18).weightPercent(40).build()
        );

        List<SyllabusWeeklySession> schedule = SyllabusScheduleFsm.buildWeeklySchedule(input, evals);

        assertNotNull(schedule);
        assertEquals(18, schedule.size());

        // Semana 1: combinación de sesión 1 y 2
        assertTrue(schedule.get(0).getTopic().contains("Introducción a Cloud Computing"));
        assertTrue(schedule.get(0).getTopic().contains("Arquitectura y Componentes"));

        // Semana 3: tag PC1
        assertEquals("PC1", schedule.get(2).getEvaluation());
        assertTrue(schedule.get(2).getTopic().contains("Almacenamiento S3 y Bloques"));

        // Semana 12: tag PC2
        assertEquals("PC2", schedule.get(11).getEvaluation());
        assertTrue(schedule.get(11).getTopic().contains("Scheduling"));

        // Semana 18: tag PROY
        assertEquals("PROY", schedule.get(17).getEvaluation());
    }
}
