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
                    int week = -1;
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
                                week = fallbackWeekForType(type);
                            }
                        }

                        // Si hay líneas secundarias que envuelven la descripción u observación
                        for (int i = 1; i < blockLines.length; i++) {
                            String extraLine = blockLines[i].trim();
                            if (extraLine.isBlank()) continue;

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
                        desc = defaultDescription(type);
                    }

                    int weight = weightMap.getOrDefault(type.toUpperCase(), 20);
                    String evalModality = determineModality(type, desc, obsText);

                    evaluations.add(SyllabusEvaluation.builder()
                            .id("eval-" + type.toLowerCase() + "-" + week)
                            .type(type.toUpperCase())
                            .description(desc)
                            .week(week)
                            .weightPercent(weight)
                            .modality(evalModality)
                            .observation(obsText.isBlank() ? evalModality + "." : obsText)
                            .rules(List.of("Semana " + week, "Ponderación " + weight + "%", evalModality, "No rezagado"))
                            .build());
                }
            }
        }

        // Fallback determinista para cualquier tipo de la fórmula no detectado en la tabla
        for (Map.Entry<String, Integer> entry : weightMap.entrySet()) {
            String type = entry.getKey();
            if (evaluations.stream().noneMatch(e -> e.getType().equalsIgnoreCase(type))) {
                int weight = entry.getValue();
                boolean isFinal = type.equalsIgnoreCase("TI") || type.equalsIgnoreCase("PROY") || type.equalsIgnoreCase("EF") || type.equalsIgnoreCase("TF");
                boolean isPA = type.equalsIgnoreCase("PA");
                int week = isFinal ? 18 : isPA ? 17 : (type.endsWith("1") ? 4 : type.endsWith("2") ? 8 : type.endsWith("3") ? 13 : 10);
                String evalModality = isFinal || type.startsWith("ATI") || type.startsWith("AP") ? "Grupal" : "Individual";
                String desc = defaultDescription(type);

                evaluations.add(SyllabusEvaluation.builder()
                        .id("eval-" + type.toLowerCase() + "-" + week)
                        .type(type.toUpperCase())
                        .description(desc)
                        .week(week)
                        .weightPercent(weight)
                        .modality(evalModality)
                        .observation(evalModality + ". Evaluación curricular (" + weight + "%).")
                        .rules(List.of("Semana " + week, "Ponderación " + weight + "%", evalModality, "No rezagado"))
                        .build());
            }
        }

        evaluations.sort(Comparator.comparingInt(SyllabusEvaluation::getWeek));
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

    private static String defaultDescription(String type) {
        if (type == null) return "EVALUACIÓN";
        if (type.equalsIgnoreCase("PA")) return "PARTICIPACIÓN EN CLASE";
        if (type.equalsIgnoreCase("TI")) return "TRABAJO DE INVESTIGACIÓN";
        if (type.equalsIgnoreCase("PROY") || type.equalsIgnoreCase("TF")) return "PROYECTO FINAL";
        if (type.equalsIgnoreCase("PTF")) return "PORTAFOLIO FINAL";
        if (type.equalsIgnoreCase("EF")) return "EXAMEN FINAL";
        if (type.equalsIgnoreCase("EP")) return "EXAMEN PARCIAL";
        if (type.startsWith("PC")) return "PRÁCTICA CALIFICADA " + type.replace("PC", "");
        if (type.startsWith("ATI")) return "AVANCE DE TRABAJO DE INVESTIGACIÓN " + type.replace("ATI", "");
        if (type.startsWith("APF")) return "AVANCE DE PROYECTO FINAL " + type.replace("APF", "");
        if (type.startsWith("AP")) return "AVANCE DE PORTAFOLIO " + type.replace("AP", "");
        if (type.startsWith("TA")) return "TAREA ACADÉMICA " + type.replace("TA", "");
        if (type.startsWith("LC")) return "LABORATORIO CALIFICADO " + type.replace("LC", "");
        return "EVALUACIÓN " + type;
    }

    private static int fallbackWeekForType(String type) {
        if (type == null) return 10;
        String t = type.toUpperCase();
        if (t.equals("TI") || t.equals("PROY") || t.equals("EF") || t.equals("TF") || t.equals("PTF")) return 18;
        if (t.equals("PA")) return 17;
        if (t.endsWith("1")) return 4;
        if (t.endsWith("2")) return 8;
        if (t.endsWith("3")) return 13;
        if (t.endsWith("4")) return 15;
        if (t.equals("EP")) return 9;
        return 10;
    }
}

