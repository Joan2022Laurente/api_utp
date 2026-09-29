package com.utp.horario.application.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.infrastructure.config.ai.OpenRouterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Selector dinámico de modelos gratuitos de OpenRouter inspirado en bajoNivel/src/openrouter.
 * Realiza scoring en tiempo real por capacidad, contexto, especialización en código/datos
 * y disponibilidad, evitando endpoints con rate-limit o descontinuados.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenRouterModelSelector {

    private final OpenRouterProperties properties;
    private final ObjectMapper objectMapper;

    private static final long CACHE_TTL_SECONDS = 1800; // 30 minutos
    private static final int MIN_CONTEXT_LENGTH = 16384;

    // Modelos con restricciones de arnés o 403 conocido
    private static final Set<String> GATED_MODELS = Set.of(
            "thinkingmachines/inkling-small:free",
            "google/lyria-3-clip-preview",
            "google/lyria-3-pro-preview"
    );

    // Fallbacks curados por excelencia técnica probada
    private static final List<String> CURATED_FALLBACK_MODELS = List.of(
            "cohere/north-mini-code:free",
            "qwen/qwen3.8-27b:free",
            "google/gemma-4-26b-a4b-it:free",
            "liquid/lfm-2.5-2.6b:free",
            "dots-studio/dots-3-note-preview:free"
    );

    private List<String> cachedRankedModels = new ArrayList<>();
    private Instant lastFetchedAt = Instant.EPOCH;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    /**
     * Retorna los mejores modelos gratuitos de OpenRouter ordenados dinámicamente por idoneidad.
     */
    public synchronized List<String> getRankedFreeModels(int limit) {
        Instant now = Instant.now();
        if (!cachedRankedModels.isEmpty() && Duration.between(lastFetchedAt, now).getSeconds() < CACHE_TTL_SECONDS) {
            return slice(cachedRankedModels, limit);
        }

        try {
            List<String> discovered = fetchAndRankModels();
            if (!discovered.isEmpty()) {
                cachedRankedModels = discovered;
                lastFetchedAt = now;
                log.info("[OpenRouterModelSelector] 🚀 {} modelos gratuitos rankeados dinámicamente: Top 3 -> {}", 
                        discovered.size(), slice(discovered, 3));
                return slice(discovered, limit);
            }
        } catch (Exception e) {
            log.warn("[OpenRouterModelSelector] Error consultando catálogo de OpenRouter (usando fallbacks curados): {}", e.getMessage());
        }

        return slice(CURATED_FALLBACK_MODELS, limit);
    }

    private List<String> fetchAndRankModels() {
        if (properties.getKeys() == null || properties.getKeys().isEmpty()) {
            return CURATED_FALLBACK_MODELS;
        }

        String authKey = properties.getKeys().get(0);
        String url = properties.getApiUrl() + "/models";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("Authorization", "Bearer " + authKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .get(6, TimeUnit.SECONDS);

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode data = root.path("data");
                if (data.isArray()) {
                    List<ScoredModel> candidates = new ArrayList<>();

                    for (JsonNode m : data) {
                        String id = m.path("id").asText("");
                        if (id.isBlank() || GATED_MODELS.contains(id)) continue;

                        // Verificar si es 100% gratuito
                        boolean isFree = id.endsWith(":free") || (
                                m.path("pricing").path("prompt").asDouble(1.0) == 0.0 &&
                                m.path("pricing").path("completion").asDouble(1.0) == 0.0
                        );
                        if (!isFree) continue;

                        // Excluir modelos de seguridad, moderación, embeddings o rerankers
                        String lowerId = id.toLowerCase();
                        if (lowerId.contains("safety") || lowerId.contains("moderation") ||
                                lowerId.contains("guard") || lowerId.contains("rerank") ||
                                lowerId.contains("embed") || lowerId.contains("openrouter/")) {
                            continue;
                        }

                        int context = m.path("context_length").asInt(0);
                        if (context < MIN_CONTEXT_LENGTH) continue;

                        double score = computeModelScore(m, id, context);
                        candidates.add(new ScoredModel(id, score));
                    }

                    candidates.sort(Comparator.comparingDouble(ScoredModel::score).reversed());
                    return candidates.stream().map(ScoredModel::id).toList();
                }
            }
        } catch (Exception e) {
            log.warn("[OpenRouterModelSelector] Fallo en descubrimiento en tiempo real: {}", e.getMessage());
        }

        return List.of();
    }

    private double computeModelScore(JsonNode node, String id, int contextLength) {
        double score = 50.0;
        String lowerId = id.toLowerCase();
        String description = node.path("description").asText("").toLowerCase();
        String name = node.path("name").asText("").toLowerCase();
        String combined = lowerId + " " + name + " " + description;

        // 1. Especialización en código, JSON y razonamiento estructurado
        if (combined.contains("code") || combined.contains("coding")) {
            score += 25.0;
        }
        if (combined.contains("reasoning") || combined.contains("instruct") || combined.contains("qwen")) {
            score += 15.0;
        }

        // 2. Modelo probado con alta confiabilidad y latencia ultra baja en pruebas
        if (id.equals("cohere/north-mini-code:free")) {
            score += 60.0; // SLA < 1.8s comprobado y 100% disponible
        } else if (id.startsWith("qwen/")) {
            score += 20.0;
        } else if (id.startsWith("google/gemma-4-26b")) {
            score += 18.0;
        }

        // 3. Bonus por ventana de contexto (hasta +10 pts)
        if (contextLength >= 262144) {
            score += 10.0;
        } else if (contextLength >= 65536) {
            score += 5.0;
        }

        // 4. Penalización de modelos hiper-pesados propensos a 429
        if (lowerId.contains("ultra") || lowerId.contains("550b") || lowerId.contains("120b")) {
            score -= 15.0;
        }

        return score;
    }

    private List<String> slice(List<String> list, int limit) {
        if (list == null || list.isEmpty()) return List.of();
        return list.subList(0, Math.min(list.size(), limit));
    }

    private record ScoredModel(String id, double score) {}
}
