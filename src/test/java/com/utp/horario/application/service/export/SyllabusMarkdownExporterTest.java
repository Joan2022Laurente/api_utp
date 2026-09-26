package com.utp.horario.application.service.export;

import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyllabusMarkdownExporterTest {

    private SyllabusMarkdownExporter exporter;

    @BeforeEach
    void setUp() {
        exporter = new SyllabusMarkdownExporter();
    }

    @Test
    @DisplayName("Debe exportar objeto Syllabus completo a formato Markdown estructurado con tablas y encabezados")
    void testExportToMarkdown() {
        Syllabus syllabus = Syllabus.builder()
                .courseCode("100000ST61")
                .courseName("DESARROLLO WEB INTEGRADO")
                .semester("2026 - Ciclo 2 Agosto")
                .credits(3)
                .modality("Presencial")
                .weeklyHours(4)
                .careers(List.of("Ingeniería de Sistemas e Informática"))
                .learningGoal("El estudiante desarrolla soluciones web integrales con Spring Boot y frontend moderno.")
                .formula("(20%)APF1 + (20%)APF2 + (20%)APF3 + (40%)PROY")
                .evaluations(List.of(
                        SyllabusEvaluation.builder()
                                .type("APF1")
                                .description("AVANCE DE PROYECTO 1")
                                .week(5)
                                .weightPercent(20)
                                .modality("Grupal")
                                .observation("Endpoints y REST")
                                .build()
                ))
                .weeklySchedule(List.of(
                        SyllabusWeeklySession.builder()
                                .week(1)
                                .session(1)
                                .unit("Unidad 1")
                                .topic("Introducción a Spring Boot")
                                .activities("Laboratorio 1")
                                .evaluation(null)
                                .build()
                ))
                .build();

        String md = exporter.exportToMarkdown(syllabus);

        assertNotNull(md);
        assertTrue(md.contains("# SÍLABO OFICIAL: DESARROLLO WEB INTEGRADO (100000ST61)"));
        assertTrue(md.contains("## 1. Información General"));
        assertTrue(md.contains("- **Créditos:** 3"));
        assertTrue(md.contains("## 2. Logro General de Aprendizaje"));
        assertTrue(md.contains("## 3. Sistema de Evaluación"));
        assertTrue(md.contains("`**(20%)APF1 + (20%)APF2 + (20%)APF3 + (40%)PROY`") || md.contains("`(20%)APF1 + (20%)APF2 + (20%)APF3 + (40%)PROY`"));
        assertTrue(md.contains("| APF1 | AVANCE DE PROYECTO 1 | 5 | 20% | Grupal | Endpoints y REST |"));
        assertTrue(md.contains("## 4. Cronograma de Actividades Semanales (18 Semanas)"));
        assertTrue(md.contains("| 1 | Unidad 1 | Introducción a Spring Boot | Laboratorio 1 | - |"));
    }

    @Test
    @DisplayName("Debe transformar texto crudo en Markdown formateado")
    void testRawTextToMarkdown() {
        String raw = """
                1. INFORMACIÓN GENERAL
                Créditos: 3
                Horas semanales: 4
                7. SISTEMA DE EVALUACIÓN
                (25%)PC1 + (25%)PC2 + (50%)PROY
                """;

        String md = exporter.rawTextToMarkdown(raw, "100000SI97");

        assertNotNull(md);
        assertTrue(md.contains("# SÍLABO OFICIAL (TEXTO EXTRAÍDO DEL PDF)"));
        assertTrue(md.contains("**Código de Asignatura:** 100000SI97"));
        assertTrue(md.contains("## 1. INFORMACIÓN GENERAL"));
        assertTrue(md.contains("## 7. SISTEMA DE EVALUACIÓN"));
    }
}
