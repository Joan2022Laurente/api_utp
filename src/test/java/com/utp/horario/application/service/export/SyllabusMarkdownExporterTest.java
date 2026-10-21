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
        assertTrue(md.contains("## 4. Cronograma de Actividades Semanales (1 Semana)"),
                "La duración del cronograma debe reflejar las semanas reales, no una constante de 18");
        assertTrue(md.contains("| 1 | Unidad 1 | Introducción a Spring Boot | Laboratorio 1 | - |"));
    }

    @Test
    @DisplayName("El encabezado del cronograma refleja la duración real (ciclo de verano de 8 semanas)")
    void testExportToMarkdownUsesRealDuration() {
        java.util.List<SyllabusWeeklySession> summerSchedule = new java.util.ArrayList<>();
        for (int w = 1; w <= 8; w++) {
            summerSchedule.add(SyllabusWeeklySession.builder()
                    .week(w)
                    .session(1)
                    .unit("Unidad " + ((w - 1) / 2 + 1))
                    .topic("Tema de la semana " + w)
                    .activities("Actividades")
                    .build());
        }

        Syllabus syllabus = Syllabus.builder()
                .courseCode("100000SI82")
                .courseName("FORMACIÓN PARA LA INVESTIGACIÓN")
                .formula("(25%)AP1 + (25%)AP2 + (50%)PTF")
                .evaluations(List.of())
                .weeklySchedule(summerSchedule)
                .build();

        String md = exporter.exportToMarkdown(syllabus);

        assertTrue(md.contains("## 4. Cronograma de Actividades Semanales (8 Semanas)"),
                "Un curso de verano de 8 semanas no debe anunciarse como 18 semanas");
        assertFalse(md.contains("18 Semanas"),
                "No debe aparecer una duración de 18 semanas en un sílabo de 8 semanas");
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
