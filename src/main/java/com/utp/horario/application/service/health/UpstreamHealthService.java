package com.utp.horario.application.service.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.infrastructure.config.UtpProperties;
import com.utp.horario.infrastructure.config.ai.OpenRouterProperties;
import com.utp.horario.infrastructure.config.supabase.SupabaseProperties;
import com.utp.horario.infrastructure.security.JwtVerificationProperties;
import com.utp.horario.presentation.dto.UpstreamHealthDto;
import com.utp.horario.presentation.dto.UpstreamHealthDto.DependencyStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Servicio de diagnóstico y evaluación en tiempo real de servicios upstream.
 *
 * <p>Sondea en paralelo todas las dependencias institucionales y de infraestructura:
 * <ul>
 *   <li>SSO Discovery (OpenID Configuration de Keycloak UTP)</li>
 *   <li>SSO JWKS (Certificados públicos y llaves activas de firma)</li>
 *   <li>Portal UTP (Gateway GraphQL institucional)</li>
 *   <li>PAO Gateway (Infraestructura UTP+ Class / AWS CloudFront)</li>
 *   <li>Supabase (Base de datos relacional de sílabos)</li>
 *   <li>OpenRouter AI (Flota de modelos LLM)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpstreamHealthService {

    private static final Duration PROBE_TIMEOUT = Duration.ofMillis(3500);

    private final JwtVerificationProperties jwtProperties;
    private final UtpProperties utpProperties;
    private final SupabaseProperties supabaseProperties;
    private final OpenRouterProperties openRouterProperties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(PROBE_TIMEOUT)
            .build();

    public UpstreamHealthDto evaluateUpstreamHealth() {
        long startTime = System.currentTimeMillis();
        Instant now = Instant.now();

        List<String> recommendations = new ArrayList<>();

        // Ejecutar todas las sondas en paralelo
        CompletableFuture<DependencyStatus> ssoDiscoveryFuture = CompletableFuture.supplyAsync(() -> probeSsoDiscovery(recommendations));
        CompletableFuture<DependencyStatus> ssoJwksFuture = CompletableFuture.supplyAsync(() -> probeSsoJwks(recommendations));
        CompletableFuture<DependencyStatus> portalGraphqlFuture = CompletableFuture.supplyAsync(() -> probePortalGraphql(recommendations));
        CompletableFuture<DependencyStatus> paoGatewayFuture = CompletableFuture.supplyAsync(() -> probePaoGateway(recommendations));
        CompletableFuture<DependencyStatus> supabaseFuture = CompletableFuture.supplyAsync(() -> probeSupabase(recommendations));
        CompletableFuture<DependencyStatus> openRouterFuture = CompletableFuture.supplyAsync(() -> probeOpenRouter(recommendations));

        CompletableFuture.allOf(
                ssoDiscoveryFuture,
                ssoJwksFuture,
                portalGraphqlFuture,
                paoGatewayFuture,
                supabaseFuture,
                openRouterFuture
        ).join();

        Map<String, DependencyStatus> dependencies = new LinkedHashMap<>();
        dependencies.put("sso_discovery", safeGet(ssoDiscoveryFuture));
        dependencies.put("sso_jwks_certs", safeGet(ssoJwksFuture));
        dependencies.put("portal_graphql", safeGet(portalGraphqlFuture));
        dependencies.put("pao_gateway", safeGet(paoGatewayFuture));
        dependencies.put("supabase_syllabi_db", safeGet(supabaseFuture));
        dependencies.put("openrouter_ai", safeGet(openRouterFuture));

        long totalDurationMs = System.currentTimeMillis() - startTime;

        // Determinar estado consolidado
        String consolidatedStatus = calculateConsolidatedStatus(dependencies);
        String summary = generateSummary(consolidatedStatus, dependencies);

        return UpstreamHealthDto.builder()
                .status(consolidatedStatus)
                .summary(summary)
                .timestamp(now.toString())
                .totalDurationMs(totalDurationMs)
                .dependencies(dependencies)
                .recommendations(recommendations)
                .build();
    }

    private DependencyStatus probeSsoDiscovery(List<String> recommendations) {
        String configuredIssuer = jwtProperties.getIssuer();
        String discoveryUrl = configuredIssuer.endsWith("/")
                ? configuredIssuer + ".well-known/openid-configuration"
                : configuredIssuer + "/.well-known/openid-configuration";

        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(discoveryUrl))
                    .timeout(PROBE_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String reportedIssuer = root.path("issuer").asText(null);
                String jwksUri = root.path("jwks_uri").asText(null);
                String tokenEndpoint = root.path("token_endpoint").asText(null);

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("reported_issuer", reportedIssuer);
                metadata.put("jwks_uri", jwksUri);
                metadata.put("token_endpoint", tokenEndpoint);

                if (reportedIssuer != null && !reportedIssuer.equals(configuredIssuer)) {
                    String warning = String.format("Discrepancia en emisor: el SSO reporta '%s' pero el gateway tiene configurado '%s'",
                            reportedIssuer, configuredIssuer);
                    recommendations.add(String.format("Actualizar UTP_JWT_ISSUER a '%s' para sincronizar con el SSO institucional.", reportedIssuer));
                    return DependencyStatus.builder()
                            .name("Keycloak SSO OpenID Discovery")
                            .url(discoveryUrl)
                            .status("WARN")
                            .httpStatus(200)
                            .latencyMs(latency)
                            .detail(warning)
                            .metadata(metadata)
                            .build();
                }

                return DependencyStatus.builder()
                        .name("Keycloak SSO OpenID Discovery")
                        .url(discoveryUrl)
                        .status("UP")
                        .httpStatus(200)
                        .latencyMs(latency)
                        .detail("Descubrimiento OIDC institucional operativo y el emisor coincide exactamente.")
                        .metadata(metadata)
                        .build();
            } else {
                recommendations.add("Verificar disponibilidad de realm Xpedition en SSO UTP o posible migración de URL.");
                return DependencyStatus.builder()
                        .name("Keycloak SSO OpenID Discovery")
                        .url(discoveryUrl)
                        .status("DOWN")
                        .httpStatus(response.statusCode())
                        .latencyMs(latency)
                        .detail("El endpoint de descubrimiento respondió con código HTTP " + response.statusCode())
                        .build();
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            recommendations.add("Revisar conectividad de red hacia " + discoveryUrl + ": " + e.getMessage());
            return DependencyStatus.builder()
                    .name("Keycloak SSO OpenID Discovery")
                    .url(discoveryUrl)
                    .status("DOWN")
                    .latencyMs(latency)
                    .detail("Error de conexión: " + e.getMessage())
                    .build();
        }
    }

    private DependencyStatus probeSsoJwks(List<String> recommendations) {
        String jwksUrl = jwtProperties.getJwksUrl();
        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jwksUrl))
                    .timeout(PROBE_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode keysNode = root.path("keys");

                List<String> activeKids = new ArrayList<>();
                if (keysNode.isArray()) {
                    for (JsonNode key : keysNode) {
                        String kid = key.path("kid").asText(null);
                        if (kid != null && !kid.isBlank()) {
                            activeKids.add(kid);
                        }
                    }
                }

                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("total_keys", activeKids.size());
                metadata.put("active_kids", activeKids);

                if (activeKids.isEmpty()) {
                    recommendations.add("El JWKS del SSO no expone llaves públicas válidas. La validación ENFORCE fallará.");
                    return DependencyStatus.builder()
                            .name("Keycloak JWKS Public Keys")
                            .url(jwksUrl)
                            .status("WARN")
                            .httpStatus(200)
                            .latencyMs(latency)
                            .detail("JWKS accesible pero sin claves públicas registradas.")
                            .metadata(metadata)
                            .build();
                }

                return DependencyStatus.builder()
                        .name("Keycloak JWKS Public Keys")
                        .url(jwksUrl)
                        .status("UP")
                        .httpStatus(200)
                        .latencyMs(latency)
                        .detail(String.format("Certificados activos y disponibles (%d llaves públicas publicadas).", activeKids.size()))
                        .metadata(metadata)
                        .build();
            } else {
                recommendations.add("Verificar endpoint UTP_JWT_JWKS_URL; el servidor respondió con HTTP " + response.statusCode());
                return DependencyStatus.builder()
                        .name("Keycloak JWKS Public Keys")
                        .url(jwksUrl)
                        .status("DOWN")
                        .httpStatus(response.statusCode())
                        .latencyMs(latency)
                        .detail("El endpoint JWKS respondió con código HTTP " + response.statusCode())
                        .build();
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            recommendations.add("Error al obtener JWKS desde " + jwksUrl + ": " + e.getMessage());
            return DependencyStatus.builder()
                    .name("Keycloak JWKS Public Keys")
                    .url(jwksUrl)
                    .status("DOWN")
                    .latencyMs(latency)
                    .detail("Error de conexión: " + e.getMessage())
                    .build();
        }
    }

    private DependencyStatus probePortalGraphql(List<String> recommendations) {
        String portalUrl = utpProperties.getPortalUrl();
        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(portalUrl))
                    .timeout(PROBE_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"{ __typename }\"}"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            int statusCode = response.statusCode();
            // 200, 400 o 401 indican que el gateway GraphQL está vivo y procesando solicitudes
            if (statusCode == 200 || statusCode == 400 || statusCode == 401) {
                return DependencyStatus.builder()
                        .name("Portal UTP GraphQL Gateway")
                        .url(portalUrl)
                        .status("UP")
                        .httpStatus(statusCode)
                        .latencyMs(latency)
                        .detail("Gateway GraphQL activo y respondiendo correctamente a las consultas del portal.")
                        .build();
            } else {
                recommendations.add(String.format("Portal GraphQL respondió con estado anómalo HTTP %d. Verificar UTP_PORTAL_URL.", statusCode));
                return DependencyStatus.builder()
                        .name("Portal UTP GraphQL Gateway")
                        .url(portalUrl)
                        .status(statusCode >= 500 ? "DOWN" : "WARN")
                        .httpStatus(statusCode)
                        .latencyMs(latency)
                        .detail("Respuesta inesperada del gateway GraphQL: HTTP " + statusCode)
                        .build();
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            recommendations.add("Fallo de conexión hacia el Portal UTP GraphQL: " + e.getMessage());
            return DependencyStatus.builder()
                    .name("Portal UTP GraphQL Gateway")
                    .url(portalUrl)
                    .status("DOWN")
                    .latencyMs(latency)
                    .detail("Error de conexión: " + e.getMessage())
                    .build();
        }
    }

    private DependencyStatus probePaoGateway(List<String> recommendations) {
        String paoUrl = utpProperties.getPaoBaseUrl().endsWith("/")
                ? utpProperties.getPaoBaseUrl() + "communication/student/ad/banners"
                : utpProperties.getPaoBaseUrl() + "/communication/student/ad/banners";

        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(paoUrl))
                    .timeout(PROBE_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            int statusCode = response.statusCode();
            // 200, 401 o 403 (MissingAuthenticationTokenException) confirman pasarela AWS activa
            if (statusCode == 200 || statusCode == 401 || statusCode == 403) {
                return DependencyStatus.builder()
                        .name("UTP+ Class PAO Gateway")
                        .url(paoUrl)
                        .status("UP")
                        .httpStatus(statusCode)
                        .latencyMs(latency)
                        .detail("Pasarela PAO UTP+ Class activa en AWS CloudFront/APIGW.")
                        .build();
            } else {
                recommendations.add(String.format("Pasarela PAO retornó HTTP %d. Verificar UTP_PAO_BASE_URL.", statusCode));
                return DependencyStatus.builder()
                        .name("UTP+ Class PAO Gateway")
                        .url(paoUrl)
                        .status(statusCode >= 500 ? "DOWN" : "WARN")
                        .httpStatus(statusCode)
                        .latencyMs(latency)
                        .detail("Respuesta inesperada de la pasarela PAO: HTTP " + statusCode)
                        .build();
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            recommendations.add("Fallo de conexión hacia la pasarela PAO: " + e.getMessage());
            return DependencyStatus.builder()
                    .name("UTP+ Class PAO Gateway")
                    .url(paoUrl)
                    .status("DOWN")
                    .latencyMs(latency)
                    .detail("Error de conexión: " + e.getMessage())
                    .build();
        }
    }

    private DependencyStatus probeSupabase(List<String> recommendations) {
        if (!supabaseProperties.isEnabled()) {
            return DependencyStatus.builder()
                    .name("Supabase Syllabi Database")
                    .url(supabaseProperties.getUrl())
                    .status("SKIPPED")
                    .detail("Integración con Supabase desactivada por configuración (SUPABASE_ENABLED=false).")
                    .build();
        }

        String tableUrl = supabaseProperties.getUrl().endsWith("/")
                ? supabaseProperties.getUrl() + "rest/v1/" + supabaseProperties.getTable() + "?select=course_code&limit=1"
                : supabaseProperties.getUrl() + "/rest/v1/" + supabaseProperties.getTable() + "?select=course_code&limit=1";

        String key = (supabaseProperties.getServiceRoleKey() != null && !supabaseProperties.getServiceRoleKey().isBlank())
                ? supabaseProperties.getServiceRoleKey()
                : supabaseProperties.getAnonKey();

        long start = System.currentTimeMillis();
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(tableUrl))
                    .timeout(PROBE_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET();

            if (key != null && !key.isBlank()) {
                reqBuilder.header("apikey", key);
                reqBuilder.header("Authorization", "Bearer " + key);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            int statusCode = response.statusCode();
            if (statusCode == 200 || statusCode == 206) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("table", supabaseProperties.getTable());
                metadata.put("authenticated", key != null && !key.isBlank());

                return DependencyStatus.builder()
                        .name("Supabase Syllabi Database")
                        .url(tableUrl)
                        .status("UP")
                        .httpStatus(statusCode)
                        .latencyMs(latency)
                        .detail("Base de datos de sílabos Supabase operativa y consultable.")
                        .metadata(metadata)
                        .build();
            } else if (statusCode == 401 || statusCode == 403) {
                recommendations.add("Revisar SUPABASE_ANON_KEY o SUPABASE_SERVICE_ROLE_KEY; credenciales rechazadas con HTTP " + statusCode);
                return DependencyStatus.builder()
                        .name("Supabase Syllabi Database")
                        .url(tableUrl)
                        .status("WARN")
                        .httpStatus(statusCode)
                        .latencyMs(latency)
                        .detail("Supabase accesible pero credenciales de autenticación no autorizadas.")
                        .build();
            } else {
                recommendations.add("Revisar estado del cluster de base de datos Supabase: HTTP " + statusCode);
                return DependencyStatus.builder()
                        .name("Supabase Syllabi Database")
                        .url(tableUrl)
                        .status(statusCode >= 500 ? "DOWN" : "WARN")
                        .httpStatus(statusCode)
                        .latencyMs(latency)
                        .detail("Respuesta inesperada de Supabase: HTTP " + statusCode)
                        .build();
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            recommendations.add("Fallo de conexión hacia Supabase: " + e.getMessage());
            return DependencyStatus.builder()
                    .name("Supabase Syllabi Database")
                    .url(tableUrl)
                    .status("DOWN")
                    .latencyMs(latency)
                    .detail("Error de conexión: " + e.getMessage())
                    .build();
        }
    }

    private DependencyStatus probeOpenRouter(List<String> recommendations) {
        if (!openRouterProperties.isEnabled()) {
            return DependencyStatus.builder()
                    .name("OpenRouter AI Fleet")
                    .url(openRouterProperties.getApiUrl())
                    .status("SKIPPED")
                    .detail("Integración con OpenRouter IA desactivada por configuración (OPENROUTER_ENABLED=false).")
                    .build();
        }

        String modelsUrl = openRouterProperties.getApiUrl().endsWith("/")
                ? openRouterProperties.getApiUrl() + "models"
                : openRouterProperties.getApiUrl() + "/models";

        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(modelsUrl))
                    .timeout(PROBE_TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            int statusCode = response.statusCode();
            if (statusCode == 200) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("primary_model", openRouterProperties.getPrimaryModel());
                metadata.put("configured_keys_count", openRouterProperties.getKeys() != null ? openRouterProperties.getKeys().size() : 0);

                return DependencyStatus.builder()
                        .name("OpenRouter AI Fleet")
                        .url(modelsUrl)
                        .status("UP")
                        .httpStatus(200)
                        .latencyMs(latency)
                        .detail("Catálogo de modelos OpenRouter accesible y flota operativa.")
                        .metadata(metadata)
                        .build();
            } else {
                recommendations.add("Revisar conectividad hacia la API de OpenRouter: HTTP " + statusCode);
                return DependencyStatus.builder()
                        .name("OpenRouter AI Fleet")
                        .url(modelsUrl)
                        .status(statusCode >= 500 ? "DOWN" : "WARN")
                        .httpStatus(statusCode)
                        .latencyMs(latency)
                        .detail("Respuesta inesperada de OpenRouter: HTTP " + statusCode)
                        .build();
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            recommendations.add("Fallo de conexión hacia OpenRouter: " + e.getMessage());
            return DependencyStatus.builder()
                    .name("OpenRouter AI Fleet")
                    .url(modelsUrl)
                    .status("WARN")
                    .latencyMs(latency)
                    .detail("Error de conexión: " + e.getMessage())
                    .build();
        }
    }

    private DependencyStatus safeGet(CompletableFuture<DependencyStatus> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return DependencyStatus.builder()
                    .status("DOWN")
                    .detail("Sonda abortada por timeout o excepción: " + e.getMessage())
                    .build();
        }
    }

    private String calculateConsolidatedStatus(Map<String, DependencyStatus> dependencies) {
        // Dependencias críticas esenciales institucionales
        boolean criticalDown = isStatus(dependencies.get("sso_discovery"), "DOWN")
                || isStatus(dependencies.get("sso_jwks_certs"), "DOWN")
                || isStatus(dependencies.get("portal_graphql"), "DOWN")
                || isStatus(dependencies.get("pao_gateway"), "DOWN");

        if (criticalDown) {
            return "DOWN";
        }

        boolean anyWarningOrNonCriticalDown = dependencies.values().stream()
                .anyMatch(dep -> "WARN".equalsIgnoreCase(dep.getStatus()) || "DOWN".equalsIgnoreCase(dep.getStatus()));

        if (anyWarningOrNonCriticalDown) {
            return "DEGRADED";
        }

        return "HEALTHY";
    }

    private boolean isStatus(DependencyStatus status, String target) {
        return status != null && target.equalsIgnoreCase(status.getStatus());
    }

    private String generateSummary(String status, Map<String, DependencyStatus> dependencies) {
        return switch (status) {
            case "HEALTHY" -> "Todas las dependencias y servicios externos upstream operan con normalidad.";
            case "DEGRADED" -> "Los servicios esenciales están operativos, pero se detectaron advertencias o servicios auxiliares degradados.";
            case "DOWN" -> "Uno o más servicios institucionales críticos de la UTP están caídos o inaccesibles.";
            default -> "Diagnóstico upstream completado.";
        };
    }
}
