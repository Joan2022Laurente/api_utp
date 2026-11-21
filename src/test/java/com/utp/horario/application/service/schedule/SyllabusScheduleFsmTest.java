package com.utp.horario.application.service.schedule;

import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyllabusScheduleFsmTest {

    @Test
    @DisplayName("Debe construir cronograma para curso de sesiones y mapear evaluaciones correctamente")
    void shouldBuildScheduleFromSessionsAndMapEvaluations() {
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
        assertEquals(4, schedule.size());

        // Semana 1: combinación de sesión 1 y 2
        assertTrue(schedule.get(0).getTopic().contains("Introducción a Cloud Computing"));
        assertTrue(schedule.get(0).getTopic().contains("Arquitectura y Componentes"));

        // Semana 3: tag PC1 (índice 1)
        assertEquals("PC1", schedule.get(1).getEvaluation());
        assertTrue(schedule.get(1).getTopic().contains("Almacenamiento S3 y Bloques"));

        // Semana 12: tag PC2 (índice 2)
        assertEquals("PC2", schedule.get(2).getEvaluation());
        assertTrue(schedule.get(2).getTopic().contains("Scheduling"));

        // Semana 18: tag PROY (índice 3)
        assertEquals("PROY", schedule.get(3).getEvaluation());
    }

    @Test
    @DisplayName("Debe detener el cronograma ante Bibliografía en minúsculas y no capturar libros como sesiones")
    void shouldStopScheduleParsingAtBibliographyOrSystemEvaluation() {
        String input = """
                8. CRONOGRAMA DE ACTIVIDADES
                1 1 Tema de Sesión 1
                1 2 Tema de Sesión 2
                Bibliografía
                1 3 Tanenbaum, A. Redes de Computadoras
                2 4 Silberschatz, A. Sistemas Operativos
                Anexos
                1 5 Anexo de Prácticas
                """;

        List<SyllabusWeeklySession> schedule = SyllabusScheduleFsm.buildWeeklySchedule(input, List.of());

        assertNotNull(schedule);
        assertEquals(2, schedule.size());
        assertEquals(1, schedule.get(0).getWeek());
        assertFalse(schedule.get(0).getTopic().contains("Tanenbaum"));
        assertFalse(schedule.get(1).getTopic().contains("Tanenbaum"));
        assertFalse(schedule.get(0).getTopic().contains("Silberschatz"));
        assertFalse(schedule.get(1).getTopic().contains("Silberschatz"));
    }
}
