package com.utp.horario.application.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanitiza y pre-procesa el volcado de texto plano extraído de PDFs oficiales de UTP,
 * eliminando ruido institucional, cabeceras repetitivas, pies de página y saltos rotos,
 * produciendo un Markdown semántico limpio y estructurado para consumo óptimo de LLMs.
 */
@Slf4j
@Component
public class SyllabusPdfToMarkdownSanitizer {

    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile(
            "(?i)(p[aá]gina\\s+\\d+(\\s+de\\s+\\d+)?|p[aá]g\\.?\\s*\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern INSTITUTIONAL_HEADER_PATTERN = Pattern.compile(
            "(?i)(universidad\\s+tecnol[oó]gica\\s+del\\s+per[uú]|vicerrectorado\\s+acad[eé]mico|direcci[oó]n\\s+acad[eé]mica|s[ií]labo\\s+del\\s+curso\\s+de\\s+[^\n]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern PRINT_TIMESTAMP_PATTERN = Pattern.compile(
            "(?i)(impreso\\s+el:?\\s*\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|fecha\\s+de\\s+descarga:?\\s*[^\n]+|sistema\\s+de\\s+gesti[oó]n\\s+acad[eé]mica)", Pattern.CASE_INSENSITIVE);

    private static final Pattern SECTION_HEADER_PATTERN = Pattern.compile(
            "(?m)^\\s*(\\d+)\\.?\\s*([A-ZÁÉÍÓÚÑ\\s]{4,50})\\s*$");

    /**
     * Convierte el volcado crudo del PDF en un Markdown depurado y estructurado.
     */
    public String sanitizeToMarkdown(String rawPdfText, String courseCode) {
        if (rawPdfText == null || rawPdfText.isBlank()) {
            return "";
        }

        // 1. Limpieza inicial de saltos Windows y caracteres de control
        String normalized = rawPdfText.replace("\r\n", "\n").replace("\r", "\n");

        // 2. Filtrado línea por línea de ruido
        String[] lines = normalized.split("\n");
        List<String> cleanedLines = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                cleanedLines.add("");
                continue;
            }

            // Descartar números de página
            if (PAGE_NUMBER_PATTERN.matcher(trimmed).matches()) {
                continue;
            }

            // Descartar marcas de tiempo / metadatos de impresión
            if (PRINT_TIMESTAMP_PATTERN.matcher(trimmed).find()) {
                continue;
            }

            // Descartar cabeceras institucionales repetidas en saltos de página
            String upper = trimmed.toUpperCase();
            if (upper.contains("UNIVERSIDAD TECNOLÓGICA DEL PERÚ") || upper.contains("UNIVERSIDAD TECNOLOGICA DEL PERU") ||
                upper.contains("VICERRECTORADO ACADÉMICO") || upper.contains("VICERRECTORADO ACADEMICO") ||
                upper.contains("DIRECCIÓN ACADÉMICA") || upper.contains("DIRECCION ACADEMICA") ||
                upper.startsWith("SÍLABO DEL CURSO") || upper.startsWith("SILABO DEL CURSO")) {
                continue;
            }

            cleanedLines.add(trimmed);
        }

        // 3. Reconstruir texto uniendo líneas cortadas y omitiendo bibliografía para no saturar al LLM
        StringBuilder sb = new StringBuilder();
        String prevLine = "";
        boolean inBibliography = false;

        for (String line : cleanedLines) {
            if (line.isEmpty()) {
                if (!prevLine.isEmpty()) {
                    sb.append("\n\n");
                    prevLine = "";
                }
                continue;
            }

            String upper = line.toUpperCase();
            if (upper.matches(".*(?:\\d+\\.\\s*)?(?:FUENTES DE INFORMACI[OÓ]N|BIBLIOGRAF[IÍ]A|PLAN DE APRENDIZAJE).*")) {
                inBibliography = true;
                continue;
            }

            if (inBibliography) {
                if (isSectionTitle(line) && !upper.contains("FUENTES") && !upper.contains("BIBLIOGRAF") && !upper.contains("PLAN DE APRENDIZAJE")) {
                    inBibliography = false;
                } else {
                    continue; // Omitir catálogo de libros e ISBNs innecesarios para estructuración de notas/sesiones
                }
            }

            boolean isTitle = isSectionTitle(line);

            if (isTitle) {
                if (!sb.toString().endsWith("\n\n") && sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append("## ").append(line).append("\n\n");
                prevLine = "";
            } else if (line.matches("(?i)^(\\-\\s+|\\*\\s+|\\d+\\.\\s+).*")) {
                // Lista con viñeta
                sb.append(line).append("\n");
                prevLine = line;
            } else if (line.matches("(?i)^(semana\\s+\\d+|unidad\\s+\\d+).*")) {
                // Hito de semana o unidad
                if (!sb.toString().endsWith("\n\n") && sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append("### ").append(line).append("\n");
                prevLine = line;
            } else {
                // Texto corrido: si la línea anterior no terminaba con puntuación fuerte, concatenar con espacio
                if (!prevLine.isEmpty() && !prevLine.endsWith(".") && !prevLine.endsWith(":") && !prevLine.endsWith(";")) {
                    sb.append(" ").append(line);
                } else {
                    sb.append(line);
                }
                prevLine = line;
            }
        }

        String result = sb.toString().trim();

        // 4. Asegurar encabezado principal con código de curso
        String header = "# SÍLABO OFICIAL UTP - " + (courseCode != null ? courseCode.toUpperCase() : "CURSO") + "\n\n";
        String finalMarkdown = header + result;

        log.debug("[SyllabusSanitizer] Texto de sílabo sanitizado: {} caracteres reducidos a {} caracteres Markdown", 
                rawPdfText.length(), finalMarkdown.length());

        return finalMarkdown;
    }

    private boolean isSectionTitle(String line) {
        if (line.length() > 60 || line.length() < 3) return false;
        String upper = line.toUpperCase();
        return upper.contains("INFORMACIÓN GENERAL") ||
               upper.contains("INFORMACION GENERAL") ||
               upper.contains("LOGRO DEL CURSO") ||
               upper.contains("LOGRO GENERAL DE APRENDIZAJE") ||
               upper.contains("UNIDADES DE APRENDIZAJE") ||
               upper.contains("SISTEMA DE EVALUACIÓN") ||
               upper.contains("SISTEMA DE EVALUACION") ||
               upper.contains("CRONOGRAMA DE ACTIVIDADES") ||
               upper.contains("METODOLOGÍA") ||
               upper.contains("METODOLOGIA") ||
               upper.contains("COMPETENCIAS");
    }
}
