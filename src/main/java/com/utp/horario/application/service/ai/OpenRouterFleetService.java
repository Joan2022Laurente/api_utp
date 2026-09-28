package com.utp.horario.application.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import com.utp.horario.infrastructure.config.ai.OpenRouterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenRouterFleetService {

    private final OpenRouterProperties properties;
    private final ObjectMapper objectMapper;

    private final AtomicInteger currentKeyIndex = new AtomicInteger(0);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    private static final String SYSTEM_PROMPT = """
            You are an elite academic syllabus extractor.
            Given the raw text of a university syllabus (UTP Perú), extract the complete academic structure into strict, clean JSON.
            Return ONLY a valid JSON object matching this schema:
            {
              "courseCode": "100000SI68",
              "courseName": "LENGUAJES DE PROGRAMACIÓN",
              "credits": 2,
              "modality": "Presencial",
              "weeklyHours": 2,
              "formula": "(25%)PC1 + (25%)PC2 + (10%)PA + (40%)PROY",
              "learningGoal": "Al finalizar el curso...",
              "evaluations": [
                {
                  "id": "PC1",
                  "type": "PC1",
                  "description": "Práctica Calificada 1",
                  "week": 4,
                  "weightPercent": 25,
                  "modality": "Individual",
                  "observation": ""
                }
              ],
              "weeklySchedule": [
                {
                  "week": 1,
                  "unit": "Unidad 1...",
                  "topic": "Temas...",
                  "activities": "Actividades...",
                  "evaluation": null
                }
              ]
            }
            Do not include markdown backticks or conversational explanations. Output JSON directly.
            """;

    /**
     * Attempts to parse raw syllabus text using the OpenRouter multi-key fleet with automatic fallback.
     */
    public Optional<Syllabus> parseSyllabusWithAi(String rawPdfText, String courseCode) {
        if (!properties.isEnabled() || properties.getKeys() == null || properties.getKeys().isEmpty()) {
            log.info("[OpenRouterFleet] IA deshabilitada o sin claves configuradas. Usando parser determinista.");
            return Optional.empty();
        }

        if (rawPdfText == null || rawPdfText.isBlank()) {
            return Optional.empty();
        }

        // Truncate if excessively long to stay within token limits
        String truncatedText = rawPdfText.length() > 25000 ? rawPdfText.substring(0, 25000) : rawPdfText;

        List<String> models = new ArrayList<>();
        if (properties.getPrimaryModel() != null && !properties.getPrimaryModel().isBlank()) {
            models.add(properties.getPrimaryModel());
        }
        if (properties.getFallbackModels() != null) {
            models.addAll(properties.getFallbackModels());
        }

        int totalKeys = properties.getKeys().size();
        int attempts = 0;
        int maxAttempts = Math.min(totalKeys * models.size(), 15);

        while (attempts < maxAttempts) {
            int keyIdx = Math.abs(currentKeyIndex.get() % totalKeys);
            String apiKey = properties.getKeys().get(keyIdx);
            String maskedKey = apiKey.length() > 14 ? apiKey.substring(0, 14) + "..." : "key-" + keyIdx;

            for (String model : models) {
                attempts++;
                try {
                    log.info("[OpenRouterFleet] 🤖 Intentando extracción con Modelo='{}' usando Clave #{}/{} ({})", 
                            model, (keyIdx + 1), totalKeys, maskedKey);

                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("model", model);
                    payload.put("messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", "Course Code: " + courseCode + "\n\nSyllabus Content:\n" + truncatedText)
                    ));
                    payload.put("temperature", 0.1);

                    String body = objectMapper.writeValueAsString(payload);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(properties.getApiUrl() + "/chat/completions"))
                            .timeout(Duration.ofSeconds(20))
                            .header("Authorization", "Bearer " + apiKey)
                            .header("Content-Type", "application/json")
                            .header("HTTP-Referer", "https://utp-academic-gateway.local")
                            .header("X-Title", "UTP Academic Gateway")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        JsonNode resJson = objectMapper.readTree(response.body());
                        JsonNode choices = resJson.path("choices");
                        if (choices.isArray() && !choices.isEmpty()) {
                            String content = choices.get(0).path("message").path("content").asText("");
                            Optional<Syllabus> parsed = parseAiResponseToDomain(content, courseCode);
                            if (parsed.isPresent()) {
                                log.info("[OpenRouterFleet] ✨ Sílabo exitosamente extraído con IA (Modelo: {}, Clave: #{}) para curso: {}", 
                                        model, (keyIdx + 1), courseCode);
                                return parsed;
                            }
                        }
                    } else if (response.statusCode() == 429 || response.statusCode() == 402 || response.statusCode() == 403) {
                        log.warn("[OpenRouterFleet] ⚠️ Límite de cuota o rate-limit alcanzado (HTTP {}) en Clave #{}. Rotando a siguiente cuenta...", 
                                response.statusCode(), (keyIdx + 1));
                        currentKeyIndex.incrementAndGet();
                        break; // Break model loop, switch to next key in while loop
                    } else {
                        log.warn("[OpenRouterFleet] Error HTTP {} en modelo {} con Clave #{}: {}", 
                                response.statusCode(), model, (keyIdx + 1), response.body());
                    }
                } catch (Exception e) {
                    log.warn("[OpenRouterFleet] Excepción en llamada a {}: {}", model, e.getMessage());
                }
            }

            // Move to next key for next iteration
            currentKeyIndex.incrementAndGet();
        }

        log.warn("[OpenRouterFleet] Agotados intentos de IA con fallback multi-cuenta. Retornando vacío para fallback determinista.");
        return Optional.empty();
    }

    private Optional<Syllabus> parseAiResponseToDomain(String jsonText, String fallbackCourseCode) {
        if (jsonText == null || jsonText.isBlank()) {
            return Optional.empty();
        }

        try {
            // Strip any markdown code fences if present (e.g. ```json ... ```)
            String cleanJson = jsonText.trim();
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.replaceFirst("^```[a-zA-Z]*\\s*", "");
                cleanJson = cleanJson.replaceFirst("\\s*```$", "");
            }

            // Find first '{' and last '}'
            int start = cleanJson.indexOf('{');
            int end = cleanJson.lastIndexOf('}');
            if (start != -1 && end != -1 && end > start) {
                cleanJson = cleanJson.substring(start, end + 1);
            }

            JsonNode root = objectMapper.readTree(cleanJson);
            String courseCode = root.path("courseCode").asText(fallbackCourseCode);
            String courseName = root.path("courseName").asText("");
            int credits = root.path("credits").asInt(3);
            String modality = root.path("modality").asText("Presencial");
            int weeklyHours = root.path("weeklyHours").asInt(4);
            String formula = root.path("formula").asText("");
            String learningGoal = root.path("learningGoal").asText("");

            List<SyllabusEvaluation> evaluations = new ArrayList<>();
            JsonNode evalsNode = root.path("evaluations");
            if (evalsNode.isArray()) {
                for (JsonNode en : evalsNode) {
                    String id = en.path("id").asText(en.path("type").asText(""));
                    String type = en.path("type").asText(id);
                    String desc = en.path("description").asText("");
                    int week = en.path("week").asInt(0);
                    int weight = en.path("weightPercent").asInt(0);
                    String mod = en.path("modality").asText("Individual");
                    String obs = en.path("observation").asText("");

                    evaluations.add(SyllabusEvaluation.builder()
                            .id(id)
                            .type(type)
                            .description(desc)
                            .week(week)
                            .weightPercent(weight)
                            .modality(mod)
                            .observation(obs)
                            .build());
                }
            }

            List<SyllabusWeeklySession> weeklySchedule = new ArrayList<>();
            JsonNode schedNode = root.path("weeklySchedule");
            if (schedNode.isArray()) {
                for (JsonNode sn : schedNode) {
                    int week = sn.path("week").asInt(1);
                    String unit = sn.path("unit").asText("");
                    String topic = sn.path("topic").asText("");
                    String activities = sn.path("activities").asText("");
                    String eval = sn.hasNonNull("evaluation") ? sn.path("evaluation").asText() : null;

                    weeklySchedule.add(SyllabusWeeklySession.builder()
                            .week(week)
                            .session(1)
                            .unit(unit)
                            .topic(topic)
                            .activities(activities)
                            .evaluation(eval)
                            .build());
                }
            }

            return Optional.of(Syllabus.builder()
                    .id(courseCode)
                    .courseCode(courseCode)
                    .courseName(courseName)
                    .credits(credits)
                    .modality(modality)
                    .weeklyHours(weeklyHours)
                    .formula(formula)
                    .learningGoal(learningGoal)
                    .evaluations(evaluations)
                    .weeklySchedule(weeklySchedule)
                    .build());
        } catch (Exception e) {
            log.error("[OpenRouterFleet] Error parseando respuesta JSON de IA: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
