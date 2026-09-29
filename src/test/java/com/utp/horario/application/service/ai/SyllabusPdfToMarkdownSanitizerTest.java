package com.utp.horario.application.service.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyllabusPdfToMarkdownSanitizerTest {

    private SyllabusPdfToMarkdownSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new SyllabusPdfToMarkdownSanitizer();
    }

    @Test
    @DisplayName("Debe eliminar cabeceras institucionales repetidas, pies de página y números de página")
    void shouldStripNoiseFromRawPdfText() {
        String dirtyText = """
                UNIVERSIDAD TECNOLÓGICA DEL PERÚ
                VICERRECTORADO ACADÉMICO
                Página 1 de 6
                Impreso el: 15/03/2026 14:30:00
                
                1. INFORMACIÓN GENERAL
                Curso: SERVICIOS CLOUD
                Código: 100000SI97
                Créditos: 3
                
                Página 2 de 6
                UNIVERSIDAD TECNOLÓGICA DEL PERÚ
                
                2. LOGRO DEL CURSO
                Al finalizar el curso el estudiante diseña arquitecturas en la nube.
                
                Pág. 3
                """;

        String clean = sanitizer.sanitizeToMarkdown(dirtyText, "100000SI97");

        assertNotNull(clean);
        assertTrue(clean.startsWith("# SÍLABO OFICIAL UTP - 100000SI97"));
        assertFalse(clean.contains("UNIVERSIDAD TECNOLÓGICA DEL PERÚ"));
        assertFalse(clean.contains("VICERRECTORADO ACADÉMICO"));
        assertFalse(clean.contains("Página 1 de 6"));
        assertFalse(clean.contains("Página 2 de 6"));
        assertFalse(clean.contains("Impreso el:"));
        assertTrue(clean.contains("## 1. INFORMACIÓN GENERAL"));
        assertTrue(clean.contains("## 2. LOGRO DEL CURSO"));
    }

    @Test
    @DisplayName("Debe reconstruir líneas partidas en oraciones y estructurar Markdown jerárquico")
    void shouldReconstructBrokenLines() {
        String brokenText = """
                1. INFORMACIÓN GENERAL
                El curso proporciona herramientas
                para diseñar soluciones basadas
                en microservicios y contenedores.
                
                Semana 1
                Introducción a la computación
                en la nube y fundamentos.
                """;

        String result = sanitizer.sanitizeToMarkdown(brokenText, "100000SI97");

        assertTrue(result.contains("El curso proporciona herramientas para diseñar soluciones basadas en microservicios y contenedores."));
        assertTrue(result.contains("### Semana 1"));
    }
}
