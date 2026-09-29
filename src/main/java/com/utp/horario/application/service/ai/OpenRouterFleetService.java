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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenRouterFleetService {

    private final OpenRouterProperties properties;
    private final OpenRouterModelSelector modelSelector;
    private final ObjectMapper objectMapper;

    private final AtomicInteger currentKeyIndex = new AtomicInteger(0);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private static final String SYSTEM_PROMPT = """
            You are an elite academic syllabus extractor.
            Given the raw text of a university syllabus (UTP Perú), extract the complete academic structure into strict, clean JSON.
            Return ONLY a valid JSON object matching this schema:
            {
              "courseCode": "100000ST99",
              "courseName": "NOMBRE DEL CURSO",
              "credits": 3,
              "modality": "Presencial",
              "weeklyHours": 4,
              "formula": "(20%)PC1 + (20%)PC2 + (20%)PC3 + (40%)PROY",
              "learningGoal": "Al finalizar el curso...",
              "evaluations": [
                {
                  "id": "PC1",
                  "type": "PC1",
                  "description": "Práctica Calificada 1",
                  "week": 4,
                  "weightPercent": 20,
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
     * Extrae y estructura un sílabo en JSON utilizando selección dinámica de modelos
     * en tiempo real con OpenRouter y rotación resiliente entre claves de la flota.
     */
    public Optional<Syllabus> parseSyllabusWithAi(String rawPdfText, String courseCode) {
        if (!properties.isEnabled() || properties.getKeys() == null || properties.getKeys().isEmpty()) {
            log.info("[OpenRouterFleet] IA deshabilitada o sin claves configuradas. Usando parser determinista.");
            return Optional.empty();
        }

        if (rawPdfText == null || rawPdfText.isBlank()) {
            return Optional.empty();
        }

        String truncatedText = rawPdfText.length() > 25000 ? rawPdfText.substring(0, 25000) : rawPdfText;

        // 1. Obtener modelos rankeados dinámicamente según catálogo en vivo y scoring (máximo 3 según API OpenRouter)
        List<String> dynamicModels = modelSelector.getRankedFreeModels(3);
        String primaryModel = !dynamicModels.isEmpty() ? dynamicModels.get(0) : "cohere/north-mini-code:free";

        int totalKeys = properties.getKeys().size();
        int attempts = 0;
        int maxAttempts = Math.min(totalKeys, 12);

        while (attempts < maxAttempts) {
            attempts++;
            int keyIdx = Math.abs(currentKeyIndex.get() % totalKeys);
            String apiKey = properties.getKeys().get(keyIdx);
            String maskedKey = apiKey.length() > 14 ? apiKey.substring(0, 14) + "..." : "key-" + keyIdx;

            try {
                log.info("[OpenRouterFleet] 🤖 Solicitando extracción IA: Modelo Primario='{}' (Fallbacks: {}) usando Clave #{}/{} ({})", 
                        primaryModel, dynamicModels, (keyIdx + 1), totalKeys, maskedKey);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("model", primaryModel);
                payload.put("models", dynamicModels.subList(0, Math.min(3, dynamicModels.size())));
                payload.put("provider", Map.of("allow_fallbacks", true));
                payload.put("messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", "Course Code: " + courseCode + "\n\nSyllabus Content:\n" + truncatedText)
                ));
                payload.put("temperature", 0.1);
                payload.put("max_tokens", 2500);

                String body = objectMapper.writeValueAsString(payload);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(properties.getApiUrl() + "/chat/completions"))
                        .timeout(Duration.ofSeconds(12))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .header("HTTP-Referer", "https://utp-academic-gateway.local")
                        .header("X-Title", "UTP Academic Gateway")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .get(12, TimeUnit.SECONDS);

                if (response.statusCode() == 200) {
                    JsonNode resJson = objectMapper.readTree(response.body());
                    JsonNode choices = resJson.path("choices");
                    if (choices.isArray() && !choices.isEmpty()) {
                        String content = choices.get(0).path("message").path("content").asText("");
                        Optional<Syllabus> parsed = parseAiResponseToDomain(content, courseCode);
                        if (parsed.isPresent()) {
                            String usedModel = resJson.path("model").asText(primaryModel);
                            log.info("[OpenRouterFleet] ✨ Sílabo exitosamente extraído con IA (Modelo activo: {}, Clave #{}) para curso: {}", 
                                    usedModel, (keyIdx + 1), courseCode);
                            return parsed;
                        }
                    }
                } else if (response.statusCode() == 429 || response.statusCode() == 402 || response.statusCode() == 403) {
                    log.warn("[OpenRouterFleet] ⚠️ Límite de cuota o rate-limit (HTTP {}) en Clave #{}. Rotando a siguiente cuenta...", 
                            response.statusCode(), (keyIdx + 1));
                    currentKeyIndex.incrementAndGet();
                } else {
                    log.warn("[OpenRouterFleet] Error HTTP {} con Clave #{}: {}", 
                            response.statusCode(), (keyIdx + 1), response.body());
                    currentKeyIndex.incrementAndGet();
                }
            } catch (Exception e) {
                log.warn("[OpenRouterFleet] Excepción en llamada IA con Clave #{}: {}", (keyIdx + 1), e.getMessage());
                currentKeyIndex.incrementAndGet();
            }
        }

        log.warn("[OpenRouterFleet] Agotados intentos de IA con flota OpenRouter. Retornando vacío para fallback determinista.");
        return Optional.empty();
    }

    private Optional<Syllabus> parseAiResponseToDomain(String jsonText, String fallbackCourseCode) {
        if (jsonText == null || jsonText.isBlank()) {
            return Optional.empty();
        }

        try {
            String cleanJson = jsonText.trim();
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.replaceFirst("^```[a-zA-Z]*\\s*", "");
                cleanJson = cleanJson.replaceFirst("\\s*```$", "");
            }

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
