package com.utp.horario.application.service;

import com.utp.horario.application.service.formula.SyllabusFormulaParser;
import com.utp.horario.application.service.pdf.PdfLayoutLine;
import com.utp.horario.application.service.pdf.PdfSpatialLayoutExtractor;
import com.utp.horario.application.service.schedule.SyllabusScheduleFsm;
import com.utp.horario.application.service.table.SyllabusTableReconstructor;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SyllabusParserEngine {

    /**
     * Parsea un binario PDF oficial de la UTP aplicando extracción espacial posicional.
     */
    public Syllabus parsePdf(byte[] pdfBytes, String fallbackCourseCode) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return buildEmptySyllabus(fallbackCourseCode);
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String rawText = stripper.getText(document);
            List<PdfLayoutLine> spatialLines = PdfSpatialLayoutExtractor.extractLines(document);
            return parse(rawText, spatialLines, fallbackCourseCode);
        } catch (IOException e) {
            log.warn("[SyllabusParserEngine] Error cargando PDDocument para '{}': {}", fallbackCourseCode, e.getMessage());
            return buildEmptySyllabus(fallbackCourseCode);
        }
    }

    /**
     * Parsea texto extraído del sílabo oficial de la UTP.
     */
    public Syllabus parse(String rawText, String fallbackCourseCode) {
        return parse(rawText, null, fallbackCourseCode);
    }

    /**
     * Parsea texto y líneas espaciales del sílabo oficial de la UTP.
     */
    public Syllabus parse(String rawText, List<PdfLayoutLine> spatialLines, String fallbackCourseCode) {
        if (rawText == null || rawText.isBlank()) {
            return buildEmptySyllabus(fallbackCourseCode);
        }

        String cleanText = rawText.replace("\r\n", "\n");

        // 1. Extraer Código y Nombre
        String courseName = "CURSO UNIVERSITARIO UTP";
        String courseCode = (fallbackCourseCode != null && !fallbackCourseCode.isBlank()) ? fallbackCourseCode : "100000";

        Pattern titlePattern = Pattern.compile("(?:#\\s*)?SÍLABO\\s*\\n?\\s*([^(]+?)\\s*\\(([A-Z0-9_]{6,12})\\)", Pattern.CASE_INSENSITIVE);
        Matcher titleMatcher = titlePattern.matcher(cleanText);
        if (titleMatcher.find()) {
            courseName = titleMatcher.group(1).trim();
            courseCode = titleMatcher.group(2).trim();
        } else {
            Pattern headerPattern = Pattern.compile("([A-ZÁÉÍÓÚÑ\\s\\-]{4,50})\\s*\\(([A-Z0-9_]{6,12})\\)");
            Matcher headerMatcher = headerPattern.matcher(cleanText);
            if (headerMatcher.find()) {
                courseName = headerMatcher.group(1).trim();
                courseCode = headerMatcher.group(2).trim();
            }
        }

        // 2. Extraer Periodo / Ciclo
        String semester = "2026 - Ciclo 2 Agosto";
        Pattern cyclePattern = Pattern.compile("(\\d{4}\\s*-\\s*Ciclo\\s*\\d+\\s*[A-Za-z]+)", Pattern.CASE_INSENSITIVE);
        Matcher cycleMatcher = cyclePattern.matcher(cleanText);
        if (cycleMatcher.find()) {
            semester = cycleMatcher.group(1).trim();
        }

        // 3. Extraer Datos Generales (Créditos, Modalidad, Horas, Carreras)
        int credits = 3;
        Pattern creditsPattern = Pattern.compile("Créditos:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher creditsMatcher = creditsPattern.matcher(cleanText);
        if (creditsMatcher.find()) {
            credits = Integer.parseInt(creditsMatcher.group(1));
        }

        String modality = "Presencial";
        Pattern modalityPattern = Pattern.compile("Enseñanza de curso:\\s*([^\\n\\r]+)", Pattern.CASE_INSENSITIVE);
        Matcher modalityMatcher = modalityPattern.matcher(cleanText);
        if (modalityMatcher.find()) {
            modality = modalityMatcher.group(1).trim();
        }

        int weeklyHours = 4;
        Pattern hoursPattern = Pattern.compile("Horas semanales:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher hoursMatcher = hoursPattern.matcher(cleanText);
        if (hoursMatcher.find()) {
            weeklyHours = Integer.parseInt(hoursMatcher.group(1));
        }

        List<String> careers = new ArrayList<>();
        Pattern careersPattern = Pattern.compile("Carrera:\\s*([\\s\\S]*?)(?=\\s*1\\.2|\\s*Créditos|\\n\\s*2\\.|\\n\\s*\\d+\\.\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher careersMatcher = careersPattern.matcher(cleanText);
        if (careersMatcher.find()) {
            careers = Arrays.stream(careersMatcher.group(1).split("\n"))
                    .map(String::trim)
                    .filter(c -> c.length() > 2)
                    .collect(Collectors.toList());
        }
        if (careers.isEmpty()) {
            careers = List.of("Ingeniería de Sistemas e Informática", "Ingeniería de Software");
        }

        // 4. Logro General de Aprendizaje
        String learningGoal = "Desarrollo de competencias profesionales y aplicación práctica en el área curricular del curso.";
        Pattern goalPattern = Pattern.compile("(?:##\\s*)?4\\.\\s*LOGRO GENERAL DE APRENDIZAJE\\s*([\\s\\S]*?)(?=(?:##\\s*)?[5-9]\\.|\\n\\s*[5-9]\\.|$)", Pattern.CASE_INSENSITIVE);
        Matcher goalMatcher = goalPattern.matcher(cleanText);
        if (goalMatcher.find()) {
            learningGoal = goalMatcher.group(1).trim().replaceAll("\\n+", " ");
        }

        // 5. Extraer y Validar Fórmula de Evaluación (Léxico y Dominio)
        SyllabusFormulaParser.FormulaParseResult formulaResult = SyllabusFormulaParser.parse(cleanText);
        String formula = !formulaResult.formulaString().isBlank() 
                ? formulaResult.formulaString() 
                : "(25%)PC1 + (25%)PC2 + (10%)PA + (40%)PROY";

        // 6. Extraer Evaluaciones y Rúbricas (Reconstructor Tabular Multilínea)
        List<SyllabusEvaluation> evaluations = SyllabusTableReconstructor.reconstructEvaluations(
                cleanText, 
                formulaResult.weightMap().isEmpty() ? SyllabusFormulaParser.parse(formula).weightMap() : formulaResult.weightMap()
        );

        // 7. Cronograma Canónico de 18 Semanas (FSM de Actividades)
        List<SyllabusWeeklySession> weeklySchedule = List.of();
        if (spatialLines != null && !spatialLines.isEmpty()) {
            weeklySchedule = SyllabusScheduleFsm.buildWeeklyScheduleFromSpatial(spatialLines, evaluations);
        }
        if (weeklySchedule == null || weeklySchedule.isEmpty()) {
            weeklySchedule = SyllabusScheduleFsm.buildWeeklySchedule(cleanText, evaluations);
        }

        List<String> rules = List.of(
                "La nota mínima aprobatoria final es de 12.",
                "En este curso, no aplica examen rezagado.",
                "El porcentaje de similitud aceptable en software antiplagio no debe exceder el 20%."
        );

        return Syllabus.builder()
                .id(courseCode)
                .courseCode(courseCode)
                .courseName(courseName)
                .semester(semester)
                .credits(credits)
                .modality(modality)
                .weeklyHours(weeklyHours)
                .careers(careers)
                .learningGoal(learningGoal)
                .formula(formula)
                .evaluations(evaluations)
                .rules(rules)
                .maxSimilarityPercent(20)
                .aiPolicy("Prohibido el uso de IA generativa para redacción o copia no documentada.")
                .weeklySchedule(weeklySchedule)
                .build();
    }

    private Syllabus buildEmptySyllabus(String courseCode) {
        String code = (courseCode != null && !courseCode.isBlank()) ? courseCode : "100000";
        return Syllabus.builder()
                .id(code)
                .courseCode(code)
                .courseName("ASIGNATURA UTP")
                .semester("2026 - Ciclo 2 Agosto")
                .credits(3)
                .modality("Oficial")
                .weeklyHours(4)
                .careers(List.of())
                .learningGoal("Sílabo en espera de sincronización con la plataforma oficial UTP.")
                .formula("")
                .evaluations(List.of())
                .rules(List.of("Nota mínima aprobatoria oficial: 12."))
                .maxSimilarityPercent(20)
                .aiPolicy("Reglamento de integridad académica UTP.")
                .weeklySchedule(List.of())
                .build();
    }
}
