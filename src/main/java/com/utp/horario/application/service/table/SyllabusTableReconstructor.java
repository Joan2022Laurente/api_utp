package com.utp.horario.application.service.table;

import com.utp.horario.domain.model.SyllabusEvaluation;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SyllabusTableReconstructor {

    private static final Pattern EVAL_SECTION_PATTERN = Pattern.compile(
            "(?:Donde:\\s*\\n?\\s*Tipo\\s+Descripci[oó]n\\s+Semana\\s+Observaci[oó]n|7\\.1\\.\\s*DESCRIPCI[OÓ]N DE LAS EVALUACIONES|7\\.\\s*SISTEMA DE EVALUACI[OÓ]N)([\\s\\S]*?)(?=Indicaciones|8\\.\\s*FUENTES|##\\s*8\\.|8\\.\\s*CRONOGRAMA|9\\.\\s*COMPETENCIAS|10\\.\\s*CRONOGRAMA|$)",
            Pattern.CASE_INSENSITIVE
    );

    public static List<SyllabusEvaluation> reconstructEvaluations(String cleanText, Map<String, Integer> weightMap) {
        List<SyllabusEvaluation> evaluations = new ArrayList<>();
        if (cleanText == null || cleanText.isBlank() || weightMap.isEmpty()) {
            return evaluations;
        }

        Set<String> validTypes = weightMap.keySet();
        Matcher sectionMatcher = EVAL_SECTION_PATTERN.matcher(cleanText);

        if (sectionMatcher.find()) {
            String block = sectionMatcher.group(1);
            String[] rawLines = block.split("\n");
            List<String> tokens = new ArrayList<>();
            for (String l : rawLines) {
                String t = l.trim();
                if (!t.isBlank() && !t.equalsIgnoreCase("Tipo") && !t.toLowerCase().startsWith("tipo descrip") 
                        && !t.toLowerCase().startsWith("donde") && !t.toLowerCase().startsWith("el cálculo")) {
                    tokens.add(t);
                }
            }

            String joined = "\n" + String.join("\n", tokens) + "\n";

            for (String type : validTypes) {
                // Delimitar el bloque textual correspondiente a este tipo hasta el siguiente tipo o fin de sección
                List<String> otherTypes = validTypes.stream().filter(t -> !t.equalsIgnoreCase(type)).map(Pattern::quote).toList();
                String delimiterRegex = otherTypes.isEmpty() 
                        ? "(?:\\n\\s*Indicaciones|\\n\\s*8\\.|$)" 
                        : "(?:\\n\\s*(?:" + String.join("|", otherTypes) + ")\\b|\\n\\s*Indicaciones|\\n\\s*8\\.|$))";

                Pattern blockPattern = Pattern.compile(
                        "(?:^|\\n)\\s*\\b" + Pattern.quote(type) + "\\b([\\s\\S]*?)(?=" + delimiterRegex,
                        Pattern.CASE_INSENSITIVE
                );
                Matcher blockMatcher = blockPattern.matcher(joined);

                if (blockMatcher.find()) {
                    String rawBlock = blockMatcher.group(1).trim();
                    if (rawBlock.isEmpty()) {
                        continue;
                    }

                    String[] blockLines = Arrays.stream(rawBlock.split("\n"))
                            .map(String::trim)
                            .filter(l -> !l.isBlank())
                            .toArray(String[]::new);

                    String desc = "";
                    Integer week = null;
                    String obsText = "";

                    if (blockLines.length > 0) {
                        String firstLine = blockLines[0].replaceAll("\\s+", " ");
                        Pattern rowPattern = Pattern.compile(
                                "^([\\p{L}\\s\\-_/.]+?(?:\\s+[1-9])?)\\s+([1-9]|1[0-8])\\b(?:\\s+(.*))?$",
                                Pattern.CASE_INSENSITIVE
                        );
                        Matcher rowMatcher = rowPattern.matcher(firstLine);

                        if (rowMatcher.find()) {
                            desc = rowMatcher.group(1).trim();
                            week = Integer.parseInt(rowMatcher.group(2));
                            obsText = rowMatcher.group(3) != null ? rowMatcher.group(3).trim() : "";
                        } else {
                            Matcher numMatcher = Pattern.compile("\\b([1-9]|1[0-8])\\b").matcher(firstLine);
                            if (numMatcher.find()) {
                                week = Integer.parseInt(numMatcher.group(1));
                                desc = firstLine.substring(0, numMatcher.start()).trim();
                                obsText = firstLine.substring(numMatcher.end()).trim();
                            } else {
                                desc = firstLine;
                                // Sin semana explícita en el PDF: se conserva null, nunca se inventa.
                                week = null;
                            }
                        }

                        // Si hay líneas secundarias que envuelven la descripción u observación
                        for (int i = 1; i < blockLines.length; i++) {
                            String extraLine = blockLines[i].trim();
                            if (extraLine.isBlank()) continue;

                            // Si la semana no se halló en la primera línea, verificar si la línea secundaria contiene el número de semana
                            if (week == null) {
                                Matcher standaloneWeekMatcher = Pattern.compile("^([1-9]|1[0-8])(?:\\s+(.*))?$").matcher(extraLine);
                                if (standaloneWeekMatcher.matches()) {
                                    week = Integer.parseInt(standaloneWeekMatcher.group(1));
                                    String rest = standaloneWeekMatcher.group(2);
                                    if (rest != null && !rest.isBlank()) {
                                        obsText = (obsText + " " + rest.trim()).trim();
                                    }
                                    continue;
                                }
                            }

                            // Comprobar si la línea secundaria contiene la continuación del nombre (ej. "INVESTIGACIÓN 1")
                            if (extraLine.toUpperCase().startsWith("INVESTIGACI") || extraLine.toUpperCase().startsWith("CALIFICADA") || extraLine.toUpperCase().startsWith("FINAL")) {
                                String[] extraParts = extraLine.split("(?i)(?=\\s+(?:Evaluaci[oó]n|Individual|Grupal|notas|entrega|registro))", 2);
                                String descContinuation = extraParts[0].trim();
                                if (!desc.contains(descContinuation)) {
                                    desc = (desc + " " + descContinuation).trim();
                                }
                                if (extraParts.length > 1) {
                                    obsText = (obsText + " " + extraParts[1].trim()).trim();
                                }
                            } else {
                                obsText = (obsText + " " + extraLine).trim();
                            }
                        }
                    }

                    if (desc.isBlank()) {
                        desc = null;
                    }

                    Integer weight = weightMap.get(type.toUpperCase());
                    if (weight == null) {
                        // El tipo no tiene ponderación declarada en la fórmula: no se inventa un 20%.
                        continue;
                    }
                    String evalModality = determineModality(type, desc != null ? desc : "", obsText);

                    evaluations.add(SyllabusEvaluation.builder()
                            .id("eval-" + type.toLowerCase() + (week != null ? "-" + week : ""))
                            .type(type.toUpperCase())
                            .description(desc)
                            .week(week)
                            .weightPercent(weight)
                            .modality(evalModality)
                            .observation(obsText.isBlank() ? null : obsText)
                            .rules(List.of())
                            .build());
                }
            }
        }

        // No se fabrican evaluaciones para tipos de la fórmula ausentes en la tabla:
        // el PDF es la única fuente de verdad y el Quality Gate rechazará el sílabo
        // si la tabla no declara todas las ponderaciones.
        evaluations.sort(Comparator.comparing(
                SyllabusEvaluation::getWeek, Comparator.nullsLast(Integer::compareTo)));
        return evaluations;
    }

    private static String determineModality(String type, String desc, String obs) {
        String combined = (type + " " + desc + " " + obs).toLowerCase();
        if (combined.contains("grupal") || combined.contains("proyecto") || combined.contains("sustentación")
                || type.equalsIgnoreCase("PROY") || type.equalsIgnoreCase("TI") || type.equalsIgnoreCase("TF")
                || type.startsWith("ATI") || type.startsWith("APF")) {
            return "Grupal";
        }
        return "Individual";
    }

}

