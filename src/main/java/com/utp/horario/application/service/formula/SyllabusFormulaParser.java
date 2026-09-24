package com.utp.horario.application.service.formula;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SyllabusFormulaParser {

    private static final Pattern FORMULA_LINE_PATTERN = Pattern.compile(
            "(?:(?:f[oó]rmula|c[aá]lculo del promedio final considera la siguiente f[oó]rmula:?)\\s*\\n?\\s*)?((?:\\(\\d{1,2}%\\)\\s*[A-Z0-9_]+\\s*\\+?\\s*){2,})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern WEIGHT_TERM_PATTERN = Pattern.compile(
            "\\((\\d{1,2})%\\)\\s*([A-Za-zÁÉÍÓÚáéíóú0-9_\\-]+)",
            Pattern.CASE_INSENSITIVE
    );

    public record FormulaParseResult(
            String formulaString,
            List<AssessmentWeight> terms,
            Map<String, Integer> weightMap,
            int totalPercentage,
            boolean isValid100Percent
    ) {}

    public static FormulaParseResult parse(String cleanText) {
        if (cleanText == null || cleanText.isBlank()) {
            return new FormulaParseResult("", List.of(), Map.of(), 0, false);
        }

        String rawFormula = "";
        Matcher lineMatcher = FORMULA_LINE_PATTERN.matcher(cleanText);
        if (lineMatcher.find()) {
            rawFormula = lineMatcher.group(1).replaceAll("\\s+", " ").trim();
        } else {
            // Intento secundario: buscar cualquier secuencia de (XX%)NOMBRE + (YY%)NOMBRE
            Pattern loosePattern = Pattern.compile("(\\(\\d{1,2}%\\)[A-Za-z0-9_]+[\\s\\S]*?\\(\\d{1,2}%\\)[A-Za-z0-9_]+)");
            Matcher looseMatcher = loosePattern.matcher(cleanText);
            if (looseMatcher.find()) {
                rawFormula = looseMatcher.group(1).replaceAll("\\n", " ").trim();
            }
        }

        List<AssessmentWeight> terms = new ArrayList<>();
        Map<String, Integer> weightMap = new LinkedHashMap<>();
        int totalPercent = 0;

        if (!rawFormula.isBlank()) {
            Matcher termMatcher = WEIGHT_TERM_PATTERN.matcher(rawFormula);
            while (termMatcher.find()) {
                int weight = Integer.parseInt(termMatcher.group(1));
                String label = termMatcher.group(2).toUpperCase().trim();
                terms.add(new AssessmentWeight(label, weight));
                weightMap.put(label, weight);
                totalPercent += weight;
            }
        }

        boolean isValid = totalPercent == 100;
        if (!isValid && !terms.isEmpty()) {
            log.warn("[SyllabusFormulaParser] La suma de ponderaciones de la fórmula es {}% (esperado: 100%). Fórmula: '{}'", totalPercent, rawFormula);
        }

        return new FormulaParseResult(rawFormula, terms, weightMap, totalPercent, isValid);
    }
}
