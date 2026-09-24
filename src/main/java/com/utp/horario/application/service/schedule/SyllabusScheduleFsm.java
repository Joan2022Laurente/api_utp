package com.utp.horario.application.service.schedule;

import com.utp.horario.application.service.pdf.PdfLayoutLine;
import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SyllabusScheduleFsm {

    public static List<SyllabusWeeklySession> buildWeeklyScheduleFromSpatial(List<PdfLayoutLine> spatialLines, List<SyllabusEvaluation> evaluations) {
        if (spatialLines == null || spatialLines.isEmpty()) {
            return List.of();
        }

        // 1. Encontrar los marcadores geométricos de cada sesión (página, Y, número de sesión)
        record SessionMarker(int session, int page, float baselineY) {}
        List<SessionMarker> markers = new ArrayList<>();

        boolean inCronograma = false;
        float minTemaX = 250.0f;
        float maxTemaX = 440.0f;
        float sesionColMinX = 220.0f;
        float sesionColMaxX = 265.0f;

        for (PdfLayoutLine line : spatialLines) {
            String lineText = line.getText();
            if (!inCronograma) {
                if (lineText.toUpperCase().contains("CRONOGRAMA DE ACTIVIDADES") || lineText.toUpperCase().contains("10. CRONOGRAMA") || lineText.toUpperCase().contains("8. CRONOGRAMA")) {
                    inCronograma = true;
                }
                continue;
            }

            if (lineText.toUpperCase().matches(".*(?:11\\.|9\\.|BIBLIOGRAFÍA|FUENTES DE INFORMACIÓN|PLAN DE APRENDIZAJE).*")) {
                break;
            }

            // Detectar límites de columnas por encabezado
            if (lineText.toLowerCase().contains("tema") && lineText.toLowerCase().contains("actividades")) {
                for (PdfLayoutLine.PdfWord w : line.getWords()) {
                    if (w.text().equalsIgnoreCase("Tema")) {
                        minTemaX = Math.max(220.0f, w.minX() - 10.0f);
                    }
                    if (w.text().toLowerCase().startsWith("actividad")) {
                        maxTemaX = Math.min(460.0f, w.minX() - 10.0f);
                    }
                    if (w.text().equalsIgnoreCase("Sesión") || w.text().equalsIgnoreCase("Sesion")) {
                        sesionColMinX = Math.max(200.0f, w.minX() - 15.0f);
                        sesionColMaxX = w.maxX() + 15.0f;
                    }
                }
                continue;
            }

            // Detectar número de sesión en la columna Sesión
            for (PdfLayoutLine.PdfWord w : line.getWords()) {
                if (w.minX() >= sesionColMinX && w.maxX() <= sesionColMaxX) {
                    try {
                        int s = Integer.parseInt(w.text().replaceAll("[^0-9]", ""));
                        int lastS = markers.isEmpty() ? 0 : markers.get(markers.size() - 1).session();
                        if (s > lastS && s <= 36) {
                            markers.add(new SessionMarker(s, line.getPage(), line.getBaselineY()));
                            break;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        if (markers.isEmpty()) {
            return List.of();
        }

        // 2. Extraer palabras de la columna Tema asociadas a cada intervalo geométrico de sesión
        Map<Integer, String> sessionMap = new HashMap<>();

        for (int i = 0; i < markers.size(); i++) {
            SessionMarker current = markers.get(i);
            SessionMarker next = (i + 1 < markers.size()) ? markers.get(i + 1) : null;

            StringBuilder topicBuilder = new StringBuilder();

            for (PdfLayoutLine line : spatialLines) {
                if (line.getPage() != current.page()) {
                    // Si el siguiente marcador está en una página posterior y esta línea está entre páginas
                    if (next != null && line.getPage() > current.page() && line.getPage() < next.page()) {
                        // Procesar líneas intermedias
                    } else {
                        continue;
                    }
                }

                // Límites de Y para la sesión actual
                boolean isSamePageAsNext = (next != null && next.page() == current.page());
                float minY = current.baselineY() - 25.0f;
                float maxY = isSamePageAsNext ? (next.baselineY() - 20.0f) : 1000.0f;

                if (line.getBaselineY() >= minY && line.getBaselineY() <= maxY) {
                    StringBuilder lineWords = new StringBuilder();
                    for (PdfLayoutLine.PdfWord w : line.getWords()) {
                        if (w.minX() >= (minTemaX - 15.0f) && w.maxX() <= (maxTemaX + 15.0f)) {
                            String txt = w.text().trim();
                            if (txt.equalsIgnoreCase("Unidad") || (txt.matches("^\\d+$") && w.minX() < minTemaX)) {
                                continue;
                            }
                            if (lineWords.length() > 0) lineWords.append(" ");
                            lineWords.append(txt);
                        }
                    }

                    String lineStr = lineWords.toString().trim();
                    if (!lineStr.isEmpty() && !isMetadataOrHeaderLine(lineStr) && !isPedagogicalNoiseLine(lineStr)) {
                        if (topicBuilder.length() > 0) {
                            if (!topicBuilder.toString().contains(lineStr)) {
                                topicBuilder.append(" ").append(lineStr);
                            }
                        } else {
                            topicBuilder.append(lineStr);
                        }
                    }
                }
            }

            if (topicBuilder.length() > 0) {
                sessionMap.put(current.session(), cleanTopicString(topicBuilder.toString()));
            }
        }

        // 3. Consolidar sesiones en 18 semanas canónicas
        Map<Integer, String> extractedTopics = new HashMap<>();
        int maxSession = sessionMap.keySet().stream().max(Integer::compareTo).orElse(0);
        boolean isMultiSession = maxSession > 18;

        for (int w = 1; w <= 18; w++) {
            if (isMultiSession) {
                int s1 = 2 * w - 1;
                int s2 = 2 * w;
                String t1 = sessionMap.getOrDefault(s1, "");
                String t2 = sessionMap.getOrDefault(s2, "");

                String combined = combineSessionTopics(t1, t2);
                if (!combined.isBlank()) {
                    extractedTopics.put(w, cleanTopicString(combined));
                }
            } else {
                String t = sessionMap.get(w);
                if (t != null && !t.isBlank()) {
                    extractedTopics.put(w, cleanTopicString(t));
                }
            }
        }

        return assembleScheduleList(extractedTopics, evaluations);
    }

    private static List<SyllabusWeeklySession> assembleScheduleList(Map<Integer, String> extractedTopics, List<SyllabusEvaluation> evaluations) {
        List<SyllabusWeeklySession> weeklySchedule = new ArrayList<>();
        for (int w = 1; w <= 18; w++) {
            String topic = extractedTopics.getOrDefault(w, "Sesión de aprendizaje y desarrollo curricular de la Semana " + w);

            String evaluationTag = null;
            for (SyllabusEvaluation ev : evaluations) {
                if (ev.getWeek() == w) {
                    evaluationTag = ev.getType();
                    break;
                }
            }

            int unitNumber = Math.min(4, Math.max(1, (int) Math.ceil(w / 4.5)));
            weeklySchedule.add(SyllabusWeeklySession.builder()
                    .week(w)
                    .session(w)
                    .unit("Unidad " + unitNumber)
                    .topic(topic)
                    .activities("Desarrollo de competencias formativas, ejercicios prácticos y retroalimentación.")
                    .evaluation(evaluationTag)
                    .build());
        }
        return weeklySchedule;
    }

    public static List<SyllabusWeeklySession> buildWeeklySchedule(String cleanText, List<SyllabusEvaluation> evaluations) {
        List<SyllabusWeeklySession> weeklySchedule = new ArrayList<>();
        Map<Integer, String> extractedTopics = new HashMap<>();

        if (cleanText != null && !cleanText.isBlank()) {
            // Estrategia 1: Bloques narrativos "Semana X ... Temario: ..."
            Pattern weekBlockPattern = Pattern.compile("Semana\\s*(\\d+)[\\s\\S]*?Temario:\\s*([^\\n]+)", Pattern.CASE_INSENSITIVE);
            Matcher weekBlockMatcher = weekBlockPattern.matcher(cleanText);
            while (weekBlockMatcher.find()) {
                int w = Integer.parseInt(weekBlockMatcher.group(1));
                String topic = weekBlockMatcher.group(2).trim();
                extractedTopics.put(w, topic);
            }

            // Estrategia 2: Tabla de actividades UTP (18 semanas o 36 sesiones con 2 sesiones/semana)
            if (extractedTopics.size() < 15) {
                Pattern schedSectionPattern = Pattern.compile("(?:CRONOGRAMA DE ACTIVIDADES)([\\s\\S]*)$", Pattern.CASE_INSENSITIVE);
                Matcher schedMatcher = schedSectionPattern.matcher(cleanText);
                if (schedMatcher.find()) {
                    String[] lines = schedMatcher.group(1).split("\n");

                    Map<Integer, String> sessionMap = new HashMap<>();
                    int currentSession = 0;
                    StringBuilder currentTopic = new StringBuilder();

                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) continue;

                        // Patrón: "W S Topic..." o "S Topic..." o "W S"
                        Matcher rowMatcher = Pattern.compile("^(?:(\\d{1,2})\\s+)?(\\d{1,2})\\s*(.*)$").matcher(trimmed);
                        if (rowMatcher.find()) {
                            String g1 = rowMatcher.group(1);
                            String g2 = rowMatcher.group(2);
                            String rest = rowMatcher.group(3) != null ? rowMatcher.group(3).trim() : "";

                            int matchedSession = -1;
                            String matchedInline = "";

                            if (g1 != null) {
                                int s = Integer.parseInt(g2);
                                if (s > currentSession && s <= 36) {
                                    matchedSession = s;
                                    matchedInline = rest;
                                }
                            } else if (g2 != null) {
                                int s = Integer.parseInt(g2);
                                if (s > currentSession && s <= 36) {
                                    matchedSession = s;
                                    matchedInline = rest;
                                }
                            }

                            if (matchedSession != -1) {
                                if (currentSession > 0 && currentTopic.length() > 0) {
                                    sessionMap.put(currentSession, cleanTopicString(currentTopic.toString()));
                                }
                                currentSession = matchedSession;
                                currentTopic = new StringBuilder(matchedInline);
                                continue;
                            }
                        }

                        // Ignorar metadatos y cabeceras de tabla
                        if (isMetadataOrHeaderLine(trimmed)) {
                            continue;
                        }

                        // Ignorar tokens sueltos de la columna de actividades (columna 5 que hace salto de línea)
                        if (isPedagogicalNoiseLine(trimmed)) {
                            continue;
                        }

                        // Acumular texto a la sesión actual
                        if (currentSession >= 1 && currentSession <= 36) {
                            if (currentTopic.length() > 0) {
                                if (!currentTopic.toString().contains(trimmed)) {
                                    currentTopic.append(" ").append(trimmed);
                                }
                            } else {
                                currentTopic.append(trimmed);
                            }
                        }
                    }

                    if (currentSession > 0 && currentTopic.length() > 0) {
                        sessionMap.put(currentSession, cleanTopicString(currentTopic.toString()));
                    }

                    int maxSession = sessionMap.keySet().stream().max(Integer::compareTo).orElse(0);
                    boolean isMultiSession = maxSession > 18;

                    for (int w = 1; w <= 18; w++) {
                        if (isMultiSession) {
                            int s1 = 2 * w - 1;
                            int s2 = 2 * w;
                            String t1 = sessionMap.getOrDefault(s1, "");
                            String t2 = sessionMap.getOrDefault(s2, "");

                            String combined = combineSessionTopics(t1, t2);
                            if (!combined.isBlank()) {
                                extractedTopics.put(w, cleanTopicString(combined));
                            }
                        } else {
                            String t = sessionMap.get(w);
                            if (t != null && !t.isBlank()) {
                                extractedTopics.put(w, cleanTopicString(t));
                            }
                        }
                    }
                }
            }
        }

        // Construir el cronograma canonical de 18 semanas
        for (int w = 1; w <= 18; w++) {
            String topic = extractedTopics.getOrDefault(w, "Sesión de aprendizaje y desarrollo curricular de la Semana " + w);
            
            // Asignar etiqueta de evaluación de la semana correspondiente
            String evaluationTag = null;
            for (SyllabusEvaluation ev : evaluations) {
                if (ev.getWeek() == w) {
                    evaluationTag = ev.getType();
                    break;
                }
            }

            int unitNumber = Math.min(4, Math.max(1, (int) Math.ceil(w / 4.5)));
            weeklySchedule.add(SyllabusWeeklySession.builder()
                    .week(w)
                    .session(w)
                    .unit("Unidad " + unitNumber)
                    .topic(topic)
                    .activities("Desarrollo de competencias formativas, ejercicios prácticos y retroalimentación.")
                    .evaluation(evaluationTag)
                    .build());
        }

        return weeklySchedule;
    }

    private static boolean isMetadataOrHeaderLine(String trimmed) {
        return trimmed.startsWith("Exposici") || trimmed.startsWith("Ejercicios") ||
                trimmed.startsWith("Actividades") || trimmed.startsWith("Revisi") ||
                trimmed.startsWith("Desarrollo de") || trimmed.startsWith("Uso del foro") ||
                trimmed.startsWith("Unidad") || trimmed.startsWith("10.CRONOGRAMA") ||
                trimmed.startsWith("Casos pr") || trimmed.startsWith("Participaci") ||
                trimmed.equalsIgnoreCase("Sesión Virtual") ||
                (trimmed.startsWith("Sesión") && trimmed.length() <= 20);
    }

    private static boolean isPedagogicalNoiseLine(String trimmed) {
        return trimmed.matches("(?i)^(?:docente|del docente|en clase|en aula|individual|grupal|virtual|presencial|retroalimentaci[oó]n|casos|casos\\.|de casos|ejercicios|de ejercicios|materiales|de materiales|foro|de consultas|evaluaci[oó]n|calificada|estudiantes|participaci[oó]n|exposici[oó]n|resoluci[oó]n|asesor[ií]a|taller|laboratorio|trabajo en equipo|avance|informe|r[uú]brica|gu[ií]a|gu[ií]as|autoevaluaci[oó]n)[\\s\\.\\:\\,\\-]*$");
    }

    private static String combineSessionTopics(String t1, String t2) {
        if (t1 == null || t1.isBlank()) return t2 == null ? "" : t2.trim();
        if (t2 == null || t2.isBlank()) return t1.trim();

        t1 = t1.trim();
        t2 = t2.trim();

        if (t1.equalsIgnoreCase(t2)) return t1;
        if (t1.toLowerCase().contains(t2.toLowerCase())) return t1;
        if (t2.toLowerCase().contains(t1.toLowerCase())) return t2;

        // Si ambas sesiones comparten el mismo prefijo temático (ej: "Introducción a ...: Tema A" y "Introducción a ...: Tema B")
        int colonIdx1 = t1.indexOf(':');
        int colonIdx2 = t2.indexOf(':');

        if (colonIdx1 > 0 && colonIdx2 > 0) {
            String prefix1 = t1.substring(0, colonIdx1).trim();
            String prefix2 = t2.substring(0, colonIdx2).trim();

            if (prefix1.equalsIgnoreCase(prefix2)) {
                String body1 = t1.substring(colonIdx1 + 1).trim();
                String body2 = t2.substring(colonIdx2 + 1).trim();
                return prefix1 + ": " + joinSentences(body1, body2);
            }
        }

        return joinSentences(t1, t2);
    }

    private static String joinSentences(String s1, String s2) {
        if (s1.isBlank()) return s2;
        if (s2.isBlank()) return s1;

        String cleanS1 = s1.replaceAll("[\\.\\s]+$", "");
        String cleanS2 = s2.replaceAll("^[\\.\\s]+", "");

        return cleanS1 + ". " + cleanS2;
    }

    private static String cleanTopicString(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String cleaned = raw.replaceAll("(?i)^(?:Sesión\\s*\\d+\\s*[:\\-]?\\s*|Semana\\s*\\d+\\s*[:\\-]?\\s*)+", "")
                .replaceAll("\\s+", " ")
                .replaceAll("(?i)\\bExposici[oó]n(?:\\s+del)?(?:\\s+docente)?\\.?\\b.*", "")
                .replaceAll("(?i)\\bEjercicios(?:\\s+pr[aá]cticos)?\\.?\\b.*", "")
                .replaceAll("(?i)\\bRevisi[oó]n(?:\\s+de\\s+los)?(?:\\s+materiales)?\\.?\\b.*", "")
                .replaceAll("(?i)\\bCasos\\s+pr[aá]cticos?\\.?\\b.*", "")
                .replaceAll("(?i)\\bDesarrollo\\s+de\\s+actividades?\\.?\\b.*", "")
                .replaceAll("(?i)\\bParticipaci[oó]n\\s+en\\s+clase(?:\\s+\\d+)?\\.?\\b.*", "")
                .replaceAll("(?i)\\bUso del foro(?:\\s+de\\s+consultas)?\\b.*", "")
                .replaceAll("(?i)\\bUnidad\\s+\\d+\\b.*", "")
                .replaceAll("^[\\s\\d]+", "")
                .replaceAll("\\b\\d{1,2}\\b$", "")
                .replaceAll("(?i)\\s+(?:docente|del docente|en clase|en aula|virtual|individual|grupal|casos|ejercicios|retroalimentaci[oó]n)[\\.\\,\\s]*$", "")
                .trim();

        if (cleaned.length() < 5) {
            cleaned = raw.replaceAll("^[\\s\\d]+", "").replaceAll("\\s+", " ").trim();
        }
        return cleaned;
    }
}
