package com.utp.horario.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.exception.AuthenticationException;
import com.utp.horario.domain.exception.ResourceNotFoundException;
import com.utp.horario.domain.exception.UpstreamServiceException;
import com.utp.horario.domain.model.AcademicActivity;
import com.utp.horario.domain.model.ClassSession;
import com.utp.horario.domain.model.Course;
import com.utp.horario.domain.model.CourseSummaryData;
import com.utp.horario.domain.model.ModalityType;
import com.utp.horario.domain.model.RubricCriterion;
import com.utp.horario.domain.model.RubricLevel;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.model.ScheduleItemParsingResult;
import com.utp.horario.domain.model.SectionCurriculumTree;
import com.utp.horario.domain.model.SectionMaterial;
import com.utp.horario.domain.model.SectionTopic;
import com.utp.horario.domain.model.SectionUnit;
import com.utp.horario.domain.model.SectionWeek;
import com.utp.horario.domain.model.StudentProfile;
import com.utp.horario.domain.model.TaskAttachment;
import com.utp.horario.domain.model.TaskSpecification;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import com.utp.horario.domain.util.CourseNameNormalizer;
import com.utp.horario.infrastructure.external.schedule.ScheduleItemParser;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class UtpPortalGatewayAdapter implements UtpPortalGatewayPort {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, String> buildingCache = new ConcurrentHashMap<>();

    // Identidad 100% determinista (RFC 4122 UUIDv5): sin cachés en memoria, sin sondas HTTP
    // y sin ficheros en disco (el filesystem de Heroku es efímero).

    private static final Pattern EVAL_PATTERN = Pattern.compile("(?i)(PC\\d*|AP\\d*|EC\\d*|TI\\d*|EP|EF|EXFN|EXPA|PROY|AVANCE|PORTAFOLIO|PARTICIPACI[OÓ]N|EXAMEN)");

    public UtpPortalGatewayAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Constructor para pruebas: permite inyectar un {@link HttpClient} simulado sin
     * levantar el contexto de Spring ni salir a la red.
     */
    UtpPortalGatewayAdapter(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    @Value("${utp.portal-url:https://api-portal.utpxpedition.com/graphql}")
    private String portalUrl;

    @Value("${utp.pao-base-url:https://api-pao.utpxpedition.com}")
    private String paoBaseUrl;

    /**
     * Identificador canónico del tenant institucional de la UTP en la plataforma PAO (Checkpoint §3 / §19).
     */
    public static final String CANONICAL_UTP_TENANT_ID = "a5f469d2-3c0e-5c68-8d32-5265923a8e40";

    @Value("${utp.tenant-id:a5f469d2-3c0e-5c68-8d32-5265923a8e40}")
    private String configuredTenantId;

    public String getInstitutionalTenantId() {
        if (configuredTenantId != null && !configuredTenantId.isBlank()) {
            return configuredTenantId.trim();
        }
        String env = System.getenv("UTP_TENANT_ID");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return CANONICAL_UTP_TENANT_ID;
    }

    private String getPortalEndpoint() {
        return (portalUrl != null && !portalUrl.isBlank()) ? portalUrl : "https://api-portal.utpxpedition.com/graphql";
    }

    private String getPaoEndpoint(String subpath) {
        String base = (paoBaseUrl != null && !paoBaseUrl.isBlank()) ? paoBaseUrl : "https://api-pao.utpxpedition.com";
        if (base.endsWith("/") && subpath.startsWith("/")) {
            return base + subpath.substring(1);
        } else if (!base.endsWith("/") && !subpath.startsWith("/")) {
            return base + "/" + subpath;
        }
        return base + subpath;
    }

    public record AcademicPeriodInfo(
            String id,
            String code,
            String name,
            boolean isCurrent,
            LocalDate startDate,
            LocalDate endDate,
            String rawCode,
            String acadCareer
    ) {
        public AcademicPeriodInfo(String id, String code, String name, boolean isCurrent, LocalDate startDate, LocalDate endDate) {
            this(id, code, name, isCurrent, startDate, endDate, code, "");
        }

        public int totalWeeks() {
            if (startDate == null || endDate == null) {
                throw new IllegalStateException("El periodo académico '" + name + "' no cuenta con fechas de inicio y fin válidas para calcular totalWeeks.");
            }
            return (int) Math.max(1, java.time.temporal.ChronoUnit.WEEKS.between(startDate, endDate) + 1);
        }
    }

    /**
     * Resuelve el periodo académico activo en tiempo real desde la API oficial de PAO.
     * Fail-Fast: lanza excepción tipada ante token inválido, 401/403 o red caída.
     * PROHIBIDO retornar objetos sintéticos con periodos hardcodeados.
     */
    public AcademicPeriodInfo resolveCurrentAcademicPeriod(String token) {
        return resolveAcademicPeriod(token, null);
    }

    /**
     * Resuelve un periodo académico específico (por id o código) o el activo si targetPeriodId es null/blanco.
     */
    public AcademicPeriodInfo resolveAcademicPeriod(String token, String targetPeriodId) {
        String effectiveToken = (token != null && token.startsWith("Bearer ")) ? token.substring(7).trim() : token;
        if (effectiveToken == null || effectiveToken.isBlank()) {
            throw new AuthenticationException("Se requiere token de sesión institucional UTP para resolver el periodo académico.");
        }
        String userId = extractUserIdFromToken(effectiveToken);
        String tenantId = extractTenantIdFromToken(effectiveToken);
        String url = getPaoEndpoint("/course/student/" + userId + "/academicperiods");

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("accept", "*/*")
                    .header("authorization", "Bearer " + effectiveToken)
                    .header("user-id", userId)
                    .header("user-role", "STUDENT")
                    .header("x-tenant-id", tenantId)
                    .header("origin", "https://class.utp.edu.pe")
                    .header("referer", "https://class.utp.edu.pe/")
                    .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("transaction-id", java.util.UUID.randomUUID().toString())
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                throw new AuthenticationException(
                        "Token de sesión UTP no autorizado o expirado al consultar periodos académicos (HTTP " + resp.statusCode() + ").");
            }
            if (resp.statusCode() == 404) {
                throw new ResourceNotFoundException(
                        "Periodos académicos no encontrados en UTP (HTTP 404).");
            }
            if (resp.statusCode() != 200) {
                throw new UpstreamServiceException(
                        "Fallo upstream al consultar los periodos académicos de UTP (HTTP " + resp.statusCode() + ").");
            }
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode array = (root != null && root.hasNonNull("data")) ? root.get("data") : root;
            if (array != null && array.isObject()) {
                if (array.hasNonNull("academicPeriods") && array.get("academicPeriods").isArray()) {
                    array = array.get("academicPeriods");
                } else if (array.hasNonNull("periods") && array.get("periods").isArray()) {
                    array = array.get("periods");
                }
            }
            if (array != null && array.isArray()) {
                List<AcademicPeriodInfo> parsedPeriods = new ArrayList<>();
                for (JsonNode node : array) {
                    String id = node.path("id").asText();
                    // UTP oficial: 'period' contiene el código canónico del ciclo (ej: "2263", "9999"), mientras 'code' es "2263PREG001"
                    String periodVal = node.hasNonNull("period") ? node.path("period").asText().trim() : "";
                    String rawCode = node.path("code").asText("");
                    String code = !periodVal.isBlank() ? periodVal : rawCode;
                    String name = node.path("name").asText();
                    String acadCareer = node.path("acadCareer").asText("");
                    String type = node.path("type").asText("");

                    // UTP oficial: 'type': "CURRENT_PERIOD" vs "OLD_PERIOD", o fallback booleano 'isCurrent'
                    boolean isCurr = "CURRENT_PERIOD".equalsIgnoreCase(type)
                            || node.path("isCurrent").asBoolean(false)
                            || "true".equalsIgnoreCase(node.path("isCurrent").asText());

                    // UTP oficial: fechas vienen como 'start' ("2026-08-10") y 'end' ("2026-12-08") o 'parsedStartDate' / 'startDate' / 'endDate'
                    String startStr = node.hasNonNull("start") ? node.path("start").asText(null)
                            : (node.hasNonNull("startDate") ? node.path("startDate").asText(null)
                            : (node.hasNonNull("parsedStartDate") ? node.path("parsedStartDate").asText(null) : null));
                    String endStr = node.hasNonNull("end") ? node.path("end").asText(null)
                            : (node.hasNonNull("endDate") ? node.path("endDate").asText(null) : null);

                    LocalDate start = null;
                    LocalDate end = null;
                    try {
                        if (startStr != null && startStr.length() >= 10) {
                            start = LocalDate.parse(startStr.substring(0, 10));
                        }
                    } catch (java.time.format.DateTimeParseException e) {
                        throw new UpstreamServiceException(
                                "Fecha de inicio del periodo '" + name + "' tiene formato ISO inválido: " + startStr, e);
                    }
                    try {
                        if (endStr != null && endStr.length() >= 10) {
                            end = LocalDate.parse(endStr.substring(0, 10));
                        }
                    } catch (java.time.format.DateTimeParseException e) {
                        throw new UpstreamServiceException(
                                "Fecha de fin del periodo '" + name + "' tiene formato ISO inválido: " + endStr, e);
                    }
                    if (id != null && !id.isBlank()) {
                        parsedPeriods.add(new AcademicPeriodInfo(id, code, name, isCurr, start, end, rawCode, acadCareer));
                    }
                }

                if (parsedPeriods.isEmpty()) {
                    throw new UpstreamServiceException(
                            "La institución no reportó periodos académicos en su catálogo.");
                }

                boolean searchSpecific = (targetPeriodId != null && !targetPeriodId.isBlank());
                if (searchSpecific) {
                    String targetClean = targetPeriodId.trim().toLowerCase();
                    // 1. Coincidencia exacta por ID, código corto, código completo o nombre
                    for (AcademicPeriodInfo p : parsedPeriods) {
                        if (p.id().equalsIgnoreCase(targetClean)
                                || (p.code() != null && p.code().equalsIgnoreCase(targetClean))
                                || (p.rawCode() != null && p.rawCode().equalsIgnoreCase(targetClean))
                                || (p.name() != null && p.name().equalsIgnoreCase(targetClean))) {
                            return validatePeriod(p);
                        }
                    }
                    // 2. Coincidencia flexible por contención o prefijo (ej. "2026 - Ciclo 2 Agosto" coincide con "2026 - Ciclo 2 Agosto PREG (001)")
                    for (AcademicPeriodInfo p : parsedPeriods) {
                        if (p.name() != null) {
                            String pNameClean = p.name().trim().toLowerCase();
                            if (pNameClean.contains(targetClean) || targetClean.contains(pNameClean)) {
                                return validatePeriod(p);
                            }
                        }
                    }
                    throw new UpstreamServiceException(
                            "El periodo académico solicitado '" + targetPeriodId + "' no fue reportado por la institución.");
                }

                // Sin periodo específico: buscar periodos con isCurrent=true (CURRENT_PERIOD)
                List<AcademicPeriodInfo> currentPeriods = parsedPeriods.stream()
                        .filter(AcademicPeriodInfo::isCurrent)
                        .toList();

                if (!currentPeriods.isEmpty()) {
                    // Si UTP marca como CURRENT_PERIOD tanto al ciclo lectivo regular como al contenedor transversal permanente 9999/PRED,
                    // priorizar el ciclo lectivo académico regular (PREG / no 9999 / fechas lectivas reales)
                    AcademicPeriodInfo activeSemester = currentPeriods.stream()
                            .filter(p -> !"9999".equals(p.code())
                                    && (p.code() == null || !p.code().startsWith("9999"))
                                    && !"PRED".equalsIgnoreCase(p.acadCareer())
                                    && (p.name() == null || !p.name().toLowerCase().contains("predeterminado"))
                                    && (p.startDate() == null || p.startDate().getYear() > 2000))
                            .findFirst()
                            .orElse(currentPeriods.get(0));

                    log.info("[resolveAcademicPeriod] Periodo activo resuelto desde la institución: '{}' (ID: {}, código: {}, carrera: {})",
                            activeSemester.name(), activeSemester.id(), activeSemester.code(), activeSemester.acadCareer());
                    return validatePeriod(activeSemester);
                }

                // Si la institución no reporta isCurrent=true (vacaciones, receso intersemestral o cambio de ciclo),
                // seleccionar el periodo lectivo regular más reciente por cronograma institucional real.
                List<AcademicPeriodInfo> validSemesterPeriods = parsedPeriods.stream()
                        .filter(p -> !"9999".equals(p.code())
                                && (p.code() == null || !p.code().startsWith("9999"))
                                && !"PRED".equalsIgnoreCase(p.acadCareer())
                                && (p.name() == null || !p.name().toLowerCase().contains("predeterminado"))
                                && (p.startDate() == null || p.startDate().getYear() > 2000))
                        .toList();

                List<AcademicPeriodInfo> candidates = validSemesterPeriods.isEmpty() ? parsedPeriods : validSemesterPeriods;
                AcademicPeriodInfo latest = candidates.stream()
                        .filter(p -> p.endDate() != null)
                        .max(Comparator.comparing(AcademicPeriodInfo::endDate))
                        .orElse(candidates.get(0));

                log.info("[resolveAcademicPeriod] Ningún periodo activo con isCurrent/CURRENT_PERIOD. Seleccionando el periodo más reciente por cronograma institucional: '{}' (ID: {}, código: {})",
                        latest.name(), latest.id(), latest.code());
                return validatePeriod(latest);
            }
            log.error("[resolveAcademicPeriod] Payload inesperado de periodos académicos (HTTP {}): {}",
                    resp.statusCode(), resp.body());
            throw new UpstreamServiceException(
                    "La respuesta de periodos académicos de UTP no es un array JSON válido: " + resp.body());
        } catch (AuthenticationException | ResourceNotFoundException | UpstreamServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamServiceException("Consulta de periodos académicos interrumpida.", e);
        } catch (Exception e) {
            throw new UpstreamServiceException(
                    "Error de red al consultar periodos académicos de UTP: " + e.getMessage(), e);
        }
    }

    private AcademicPeriodInfo validatePeriod(AcademicPeriodInfo p) {
        if (p.startDate() == null) {
            throw new UpstreamServiceException(
                    "El periodo académico '" + p.name() + "' de UTP no incluye fecha de inicio (startDate) válida.");
        }
        if (p.endDate() == null) {
            throw new UpstreamServiceException(
                    "El periodo académico '" + p.name() + "' de UTP no incluye fecha de fin (endDate) válida.");
        }
        return p;
    }

    /**
     * Namespace UUID v5 de la plataforma UTP+Class, usado para derivar el {@code paoUserId}.
     */
    @Value("${utp.pao-uuid-namespace:}")
    private String paoUuidNamespace;

    /**
     * Configura el namespace UUIDv5 de la derivación (Checkpoint §19). No hay mapas,
     * sondas ni caché en disco: la identidad se recalcula en 0 ms por petición.
     */
    @PostConstruct
    public void loadPaoUserOverrides() {
        String envNamespace = System.getenv("UTP_PAO_UUID_NAMESPACE");
        if ((envNamespace == null || envNamespace.isBlank())
                && paoUuidNamespace != null && !paoUuidNamespace.isBlank()) {
            System.setProperty("utp.pao-uuid-namespace", paoUuidNamespace.trim());
        }
    }

    // ==================================================================
    // IDENTIDAD DETERMINISTA (RFC 4122 UUIDv5) â€” SIN CACHÉS, SONDA NI DISCO
    // ==================================================================

    /**
     * Resuelve el {@code paoUserId} del alumno mediante derivación matemática directa
     * (Checkpoint §19): {@code uuidv5(namespace, UPPER(codigo) + "@UTP.EDU.PE")}.
     * Cero latencia, cero llamadas de red y cero estado compartido entre alumnos.
     */
    String extractUserIdFromToken(String token) {
        if (token == null || !token.contains(".")) {
            throw new AuthenticationException("El token de sesión UTP no contiene un identificador de usuario válido.");
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                JsonNode payload = objectMapper.readTree(decoded);

                // Checkpoint §19: la semilla es SIEMPRE el codigo de alumno del claim,
                // no el claim email (que podria ser un alias distinto al oficial).
                String code = payload.hasNonNull("preferred_username")
                        ? payload.path("preferred_username").asText()
                        : (payload.hasNonNull("username") ? payload.path("username").asText() : "");
                String seed = !code.isBlank()
                        ? code.trim() + "@UTP.EDU.PE"
                        : (payload.hasNonNull("email") ? payload.path("email").asText() : null);
                String derived = PaoUserIdDeriver.derive(seed);
                if (!derived.isBlank()) {
                    return derived;
                }
            }
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthenticationException("No fue posible derivar el paoUserId del token de sesión UTP.", e);
        }
        throw new AuthenticationException("El token de sesión UTP no contiene un identificador de usuario válido.");
    }

    private String extractStudentCodeFromToken(String token) {
        if (token == null || !token.contains(".")) {
            throw new AuthenticationException("El token de sesión UTP no contiene código de estudiante válido.");
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                JsonNode payload = objectMapper.readTree(decoded);
                if (payload.hasNonNull("preferred_username")) {
                    String val = payload.path("preferred_username").asText().trim().toUpperCase();
                    if (val.matches("^[U]?\\d{8,9}$")) return val;
                }
                if (payload.hasNonNull("username")) {
                    String val = payload.path("username").asText().trim().toUpperCase();
                    if (val.matches("^[U]?\\d{8,9}$")) return val;
                }
                if (payload.hasNonNull("sub")) {
                    String sub = payload.path("sub").asText().trim().toUpperCase();
                    if (sub.matches("^[U]?\\d{8,9}$")) {
                        return sub;
                    }
                }
            }
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthenticationException("No fue posible leer el código de estudiante del token UTP.", e);
        }
        throw new AuthenticationException("El token de sesión UTP no contiene código de estudiante válido.");
    }

    /**
     * Resuelve el tenant institucional para las peticiones hacia la plataforma PAO (cabecera {@code x-tenant-id}).
     *
     * <p>Prioridad 1: Si el token JWT declara un claim explícito de tenant ("tenantId", "tenant_id",
     * "tenant", "x-tenant-id"), se respeta y retorna dicho valor.</p>
     * <p>Prioridad 2: En ausencia del claim en el token (comportamiento estándar del SSO Keycloak UTP
     * para alumnos en producción), resuelve el tenant institucional centralizado de la UTP
     * ({@link #CANONICAL_UTP_TENANT_ID}), sobreescribible mediante {@code utp.tenant-id} o la variable
     * de entorno {@code UTP_TENANT_ID}.</p>
     */
    String extractTenantIdFromToken(String token) {
        if (token != null && token.contains(".")) {
            try {
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                    JsonNode payload = objectMapper.readTree(decoded);
                    for (String claim : new String[]{"tenantId", "tenant_id", "tenant", "x-tenant-id"}) {
                        JsonNode value = payload.get(claim);
                        if (value != null && value.isValueNode() && !value.asText().isBlank()) {
                            return value.asText().trim();
                        }
                    }
                }
            } catch (Exception ignored) {
                // Token payload no parseable para claims de tenant; fallback al tenant institucional configurado.
            }
        }
        return getInstitutionalTenantId();
    }

    @Override
    public StudentProfile login(String username, String password) {
        String cleanUsername = username.trim().toLowerCase();
        if (cleanUsername.matches("^\\d{8,9}$")) {
            cleanUsername = "u" + cleanUsername;
        }

        List<String> tokenEndpoints = List.of(
                "https://auth.utp.edu.pe/realms/Xpedition/protocol/openid-connect/token",
                "https://sso.utp.edu.pe/auth/realms/Xpedition/protocol/openid-connect/token"
        );

        String formParams = "client_id=" + URLEncoder.encode("pao-web", StandardCharsets.UTF_8)
                + "&grant_type=" + URLEncoder.encode("password", StandardCharsets.UTF_8)
                + "&username=" + URLEncoder.encode(cleanUsername, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password.trim(), StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode("openid roles-client-pao-web profile email roles-realm-xpedition", StandardCharsets.UTF_8);

        for (String endpoint : tokenEndpoints) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(formParams))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    String accessToken = root.path("access_token").asText();
                    String refreshToken = root.hasNonNull("refresh_token") ? root.path("refresh_token").asText() : null;
                    int expiresIn = root.hasNonNull("expires_in") ? root.path("expires_in").asInt(1800) : 1800;

                    StudentProfile base = parseProfileFromTokens(accessToken, refreshToken, expiresIn, cleanUsername);
                    // El JWT pao-web NO incluye career/campus/cycle â€” enriquecer desde Portal GraphQL
                    StudentProfile profile = enrichProfileFromPortal(base, accessToken);
                    log.info("[UTP SSO Auth] Autenticación exitosa en {}: {} | campus='{}' | carrera='{}' | ciclo={}",
                            endpoint, profile.getStudentCode(), profile.getCampus(), profile.getCareer(), profile.getCurrentCycle());
                    return profile;
                } else if (response.statusCode() == 400 || response.statusCode() == 401 || response.statusCode() == 403) {
                    log.warn("[UTP SSO Auth] Credenciales rechazadas en {}, status: {}", endpoint, response.statusCode());
                } else {
                    log.warn("[UTP SSO Auth] Falló autenticación en {}, status: {}", endpoint, response.statusCode());
                }
            } catch (Exception e) {
                log.error("[UTP SSO Auth] Error conectando con {}: {}", endpoint, e.getMessage());
            }
        }
        throw new AuthenticationException("Credenciales UTP inválidas o servicio de autenticación no disponible.");
    }

    /**
     * Normaliza el token de sesión quitando el prefijo "Bearer " si lo lleva.
     * Si la petición no trae token, devuelve cadena vacía (petición anónima).
     */
    private String stripBearerPrefix(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String raw = token.trim();
        if (raw.regionMatches(true, 0, "Bearer ", 0, 7)) {
            raw = raw.substring(7).trim();
        }
        return raw;
    }

    @Override
    public StudentProfile refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthenticationException("Refresh token inválido o vacío");
        }

        List<String> tokenEndpoints = List.of(
                "https://auth.utp.edu.pe/realms/Xpedition/protocol/openid-connect/token",
                "https://sso.utp.edu.pe/auth/realms/Xpedition/protocol/openid-connect/token"
        );

        String formParams = "client_id=" + URLEncoder.encode("pao-web", StandardCharsets.UTF_8)
                + "&grant_type=" + URLEncoder.encode("refresh_token", StandardCharsets.UTF_8)
                + "&refresh_token=" + URLEncoder.encode(refreshToken.trim(), StandardCharsets.UTF_8);

        for (String endpoint : tokenEndpoints) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(formParams))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    String accessToken = root.path("access_token").asText();
                    String newRefreshToken = root.hasNonNull("refresh_token") ? root.path("refresh_token").asText() : refreshToken;
                    int expiresIn = root.hasNonNull("expires_in") ? root.path("expires_in").asInt(1800) : 1800;

                    StudentProfile profile = parseProfileFromTokens(accessToken, newRefreshToken, expiresIn, "");
                    log.info("[UTP SSO Refresh] Token renovado exitosamente en {} para alumno: {}", endpoint, profile.getStudentCode());
                    return profile;
                } else if (response.statusCode() == 400 || response.statusCode() == 401 || response.statusCode() == 403) {
                    log.warn("[UTP SSO Refresh] Refresh token rechazado en {}, status: {}", endpoint, response.statusCode());
                } else {
                    log.warn("[UTP SSO Refresh] Falló renovación en {}, status: {}", endpoint, response.statusCode());
                }
            } catch (Exception e) {
                log.error("[UTP SSO Refresh] Error conectando con {}: {}", endpoint, e.getMessage());
            }
        }
        throw new AuthenticationException("Refresh token expirado o inválido en Keycloak UTP.");
    }

    private StudentProfile parseProfileFromTokens(String accessToken, String refreshToken, int expiresIn, String defaultCode) {
        String studentName = defaultCode.toUpperCase();
        String studentCode = defaultCode.toUpperCase();
        String studentEmail = defaultCode.isBlank() ? "" : defaultCode.toLowerCase() + "@utp.edu.pe";
        String studentUserId = "";
        String studentCareer = "";
        String studentCampus = "";
        int studentCycle = 1;

        if (accessToken != null && accessToken.contains(".")) {
            try {
                String[] parts = accessToken.split("\\.");
                if (parts.length >= 2) {
                    byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                    JsonNode payload = objectMapper.readTree(decoded);
                    if (payload.hasNonNull("name")) studentName = payload.path("name").asText();
                    if (payload.hasNonNull("email")) studentEmail = payload.path("email").asText();
                    if (payload.hasNonNull("preferred_username")) {
                        studentCode = payload.path("preferred_username").asText().toUpperCase();
                        if (studentEmail.isBlank()) studentEmail = studentCode.toLowerCase() + "@utp.edu.pe";
                    }
                    if (payload.hasNonNull("userId")) {
                        studentUserId = payload.path("userId").asText();
                    } else if (payload.hasNonNull("user_id")) {
                        studentUserId = payload.path("user_id").asText();
                    } else if (payload.hasNonNull("paoUserId")) {
                        studentUserId = payload.path("paoUserId").asText();
                    } else if (payload.hasNonNull("sub")) {
                        studentUserId = payload.path("sub").asText();
                    }

                    if (payload.hasNonNull("career")) studentCareer = payload.path("career").asText();
                    else if (payload.hasNonNull("carrera")) studentCareer = payload.path("carrera").asText();

                    if (payload.hasNonNull("campus")) studentCampus = payload.path("campus").asText();
                    else if (payload.hasNonNull("campusDesc")) studentCampus = payload.path("campusDesc").asText();
                    else if (payload.hasNonNull("sede")) studentCampus = payload.path("sede").asText();

                    if (payload.hasNonNull("cycle")) studentCycle = payload.path("cycle").asInt(1);
                    else if (payload.hasNonNull("ciclo")) studentCycle = payload.path("ciclo").asInt(1);
                }
            } catch (Exception ignored) {}
        }

        if (studentUserId.isBlank()) {
            if (!studentCode.isBlank()) {
                studentUserId = studentCode;
            } else {
                throw new AuthenticationException("El token de sesión UTP no contiene un identificador de usuario válido.");
            }
        }

        return StudentProfile.builder()
                .id(studentUserId)
                .studentCode(studentCode)
                .fullName(studentName)
                .email(studentEmail)
                .career(studentCareer)
                .campus(studentCampus)
                .currentCycle(studentCycle)
                .token(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn)
                .enrolledCourseCodes(List.of())
                .build();
    }

    /**
     * Enriquece el perfil base (extraído del JWT) con career, campus y currentCycle
     * obtenidos desde el Portal GraphQL del estudiante UTP.
     * Llamadas: getProfile (academic.progDesc, campusDesc) + GetCourseSummary (relativeCycle).
     */
    private StudentProfile enrichProfileFromPortal(StudentProfile base, String accessToken) {
        String career = base.getCareer();
        String campus = base.getCampus();
        int cycle = base.getCurrentCycle() != null ? base.getCurrentCycle() : 1;
        String studentCode = base.getStudentCode() != null ? base.getStudentCode().trim().toUpperCase() : "";

        String portalEndpoint = getPortalEndpoint();
        Map<String, String> commonHeaders = Map.of(
                "applicationid", "APP00002",
                "authorization", "Bearer " + accessToken,
                "content-type", "application/json",
                "isreservation", "false",
                "user-id", studentCode,
                "user-role", "student"
        );

        // 1. getProfile â†’ academic.progDesc (carrera legible) + campusDesc
        try {
            String profileQuery = "{\"operationName\":\"getProfile\",\"variables\":{}," +
                    "\"query\":\"query getProfile { student { academic { campusDesc progDesc } } }\"}";

            HttpRequest.Builder profileReqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(portalEndpoint))
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(profileQuery));
            commonHeaders.forEach(profileReqBuilder::header);
            HttpResponse<String> profileResp = httpClient.send(profileReqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (profileResp.statusCode() == 200) {
                JsonNode academic = objectMapper.readTree(profileResp.body())
                        .path("data").path("student").path("academic");
                if (!academic.isMissingNode()) {
                    String progDesc = academic.path("progDesc").asText("").trim();
                    String campusDesc = academic.path("campusDesc").asText("").trim();
                    if (!progDesc.isBlank()) career = progDesc;
                    if (!campusDesc.isBlank()) campus = campusDesc;
                }
            }
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[UTP Portal Enrich] No se pudo obtener getProfile: {}", e.getMessage());
        }

        // 2. GetCourseSummary → summary.relativeCycle (ciclo real) + summary.campus (fallback)
        try {
            String enrichPeriod = resolveCurrentAcademicPeriod(accessToken).id();
            String summaryQuery = "{\"operationName\":\"GetCourseSummary\",\"variables\":{\"periodId\":\"" + enrichPeriod + "\"}," +
                    "\"query\":\"query GetCourseSummary($periodId: String!) { getCourseSummary(periodId: $periodId) { summary { campus relativeCycle } } }\"}";

            HttpRequest.Builder summaryReqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(portalEndpoint))
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(summaryQuery));
            commonHeaders.forEach(summaryReqBuilder::header);
            HttpResponse<String> summaryResp = httpClient.send(summaryReqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (summaryResp.statusCode() == 200) {
                JsonNode summary = objectMapper.readTree(summaryResp.body())
                        .path("data").path("getCourseSummary").path("summary");
                if (!summary.isMissingNode()) {
                    String relativeCycleRaw = summary.path("relativeCycle").asText("").trim();
                    if (!relativeCycleRaw.isBlank()) {
                        try { cycle = Integer.parseInt(relativeCycleRaw.replaceAll("\\D", "")); } catch (NumberFormatException ignored) {}
                    }
                    if (campus.isBlank()) {
                        String campusFallback = summary.path("campus").asText("").trim();
                        if (!campusFallback.isBlank()) campus = campusFallback;
                    }
                }
            }
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[UTP Portal Enrich] No se pudo obtener GetCourseSummary: {}", e.getMessage());
        }

        return StudentProfile.builder()
                .id(base.getId())
                .studentCode(base.getStudentCode())
                .fullName(base.getFullName())
                .email(base.getEmail())
                .career(career)
                .campus(campus)
                .currentCycle(cycle)
                .token(base.getToken())
                .refreshToken(base.getRefreshToken())
                .expiresIn(base.getExpiresIn())
                .enrolledCourseCodes(base.getEnrolledCourseCodes())
                .build();
    }

    @Override
    public ScheduleInterval fetchSchedule(String token, String period) {
        if (token == null || token.isBlank()) {
            throw new AuthenticationException("Se requiere token de sesión institucional UTP.");
        }
        if (!token.contains(".")) {
            throw new AuthenticationException("El token de sesión UTP no tiene formato JWT.");
        }
        // Directiva Checkpoint §18.3: scheduleByDate es la ÚNICA fuente del horario.
        // No existe caída a /course/student/calendar: entregar deadlines como clases
        // sería un fallback engañoso (sin aula, edificio, docente ni franja horaria).
        return fetchScheduleFromPortalGraphQL(token, period);
    }

    /** Proyección verificada de scheduleByDate (Checkpoint §8.8 + Ruptura 2). */
    private static final String SCHEDULE_GRAPHQL_QUERY = """
            query getSchedules($date: Float!, $periods: [String!]) {
              scheduleByDate(filters: {date: $date, classTypes: [1, 2, 3, 4, 5, 6], periods: $periods}) {
                dates {
                  date
                  items {
                    name
                    period
                    modality { id }
                    startTime
                    endTime
                    class {
                      id
                      start
                      end
                      descAmb
                      classType
                      instructionMode
                      linkZoom
                      linkCourseClass
                      location {
                        classRoom { id desc floor }
                        building { id desc address }
                      }
                      professors { firstName lastName }
                    }
                  }
                }
              }
            }
            """;

    private ScheduleInterval fetchScheduleFromPortalGraphQL(String token, String period) {
        String studentCode = extractStudentCodeFromToken(token);
        if (studentCode.isBlank()) {
            throw new AuthenticationException("El token de sesión UTP no contiene código de estudiante válido.");
        }

        ensureBuildingsLoaded(token, studentCode);

        String targetPeriod = (period != null && !period.isBlank()) ? period.trim() : null;
        AcademicPeriodInfo periodInfo = resolveAcademicPeriod(token, targetPeriod);
        String currentPeriod = periodInfo.id();
        // Ruptura 5 (Checkpoint §13): conjunto compuesto de periodos, sin filtrar por
        // flag `active` (siempre true en Canvas) y preservando talleres 9999.
        Set<String> targetPeriods = new LinkedHashSet<>();
        targetPeriods.add(currentPeriod);
        targetPeriods.add("9999");

        // Checkpoint 5: un fallo del catalogo institucional (401/403/404/5xx/red) se
        // propaga como excepcion tipada; NUNCA se degrada a un horario sin courseCode.
        List<DashboardCourseRef> dashboard = loadDashboardCourses(token, targetPeriods);
        boolean dashboardUpstreamFailed = false;

        Map<String, DashboardCourseRef> dashboardBySectionId = dashboard.stream()
                .filter(r -> r.sectionId() != null && !r.sectionId().isBlank())
                .collect(Collectors.toMap(DashboardCourseRef::sectionId, r -> r, (a, b) -> a, LinkedHashMap::new));
        Map<String, Course> sectionCatalog = dashboardBySectionId.values().stream()
                .map(ref -> Course.builder()
                        .id("c-" + (ref.sectionId() == null ? "" : ref.sectionId()))
                        .code(ref.courseCode())
                        .name(ref.name())
                        .section(ref.classNumber())
                        .sectionId(ref.sectionId())
                        .modalityType(ref.modality())
                        .build())
                .filter(c -> c.getSectionId() != null)
                .collect(Collectors.toMap(Course::getSectionId, c -> c, (a, b) -> a, LinkedHashMap::new));

        Map<String, Course> nameCatalog = dashboard.stream()
                .filter(r -> r.name() != null && !r.name().isBlank())
                .map(r -> Course.builder()
                        .code(r.courseCode())
                        .name(r.name())
                        .section(r.classNumber())
                        .sectionId(r.sectionId())
                        .modalityType(r.modality())
                        .build())
                .collect(Collectors.toMap(
                        c -> CourseNameNormalizer.normalize(c.getName()),
                        c -> c, (a, b) -> a, LinkedHashMap::new));

        ScheduleItemParser parser = new ScheduleItemParser(sectionCatalog, nameCatalog, buildingCache);
        List<ClassSession> allClasses = new ArrayList<>();
        Map<String, Course> coursesMap = new LinkedHashMap<>();
        java.util.concurrent.atomic.AtomicInteger rejectedCountRef = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger unresolvedBuildingRef = new java.util.concurrent.atomic.AtomicInteger();
        RuntimeException lastFailure = null;

        LocalDate periodStartDate = periodInfo.startDate();
        LocalDate periodEndDate = periodInfo.endDate();
        int totalWeeks = periodInfo.totalWeeks();
        long semesterStartMillis = periodStartDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
        long nowMillis = System.currentTimeMillis();
        int currentWeekCalc = (int) Math.max(1, Math.min(totalWeeks,
                ((nowMillis - semesterStartMillis) / (7L * 24 * 60 * 60 * 1000L)) + 1));

        for (int w = 0; w < totalWeeks; w++) {
            long weekTimestamp = semesterStartMillis + (w * 7L * 24 * 60 * 60 * 1000L);
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("date", weekTimestamp);
            vars.put("periods", List.of(currentPeriod));
            Map<String, Object> bodyObj = new LinkedHashMap<>();
            bodyObj.put("operationName", "getSchedules");
            bodyObj.put("variables", vars);
            bodyObj.put("query", SCHEDULE_GRAPHQL_QUERY);

            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(getPortalEndpoint()))
                        .timeout(Duration.ofSeconds(10))
                        .header("accept", "*/*")
                        .header("applicationid", "APP00002")
                        .header("authorization", "Bearer " + token)
                        .header("content-type", "application/json")
                        .header("isreservation", "false")
                        .header("user-id", studentCode)
                        .header("user-role", "student")
                        .header("Referer", "https://portal.utp.edu.pe/")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyObj)))
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                    throw new AuthenticationException(
                            "Sesión institucional UTP expirada o no autorizada en Portal GraphQL (HTTP "
                                    + resp.statusCode() + ").");
                }
                if (resp.statusCode() != 200) {
                    lastFailure = new UpstreamServiceException(
                            "El Portal UTP no está disponible (HTTP " + resp.statusCode()
                                    + ") al consultar la semana " + (w + 1) + ".");
                    continue;
                }

                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode dates = root.path("data").path("scheduleByDate").path("dates");
                if (root.has("errors") && (dates.isMissingNode() || dates.isEmpty())) {
                    lastFailure = new UpstreamServiceException(
                            "El Portal UTP devolvió errores al consultar la semana " + (w + 1) + ".");
                    continue;
                }
                if (!dates.isArray()) {
                    continue;
                }
                for (JsonNode dateNode : dates) {
                    JsonNode items = dateNode.path("items");
                    if (!items.isArray()) {
                        continue;
                    }
                    for (JsonNode item : items) {
                        handleParsedItem(parser.parse(item), sectionCatalog, allClasses, coursesMap,
                                rejectedCountRef, unresolvedBuildingRef);
                    }
                }
            } catch (AuthenticationException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UpstreamServiceException("Consulta de horario interrumpida.", e);
            } catch (Exception e) {
                lastFailure = new UpstreamServiceException(
                        "Error de conexión al consultar la semana " + (w + 1) + " en el Portal UTP: "
                                + e.getMessage(), e);
                log.warn("[UTP Portal GraphQL] Falló consulta de semana {}: {}", w + 1, e.getMessage());
            }
        }

        if (allClasses.isEmpty() && coursesMap.isEmpty() && lastFailure != null) {
            throw new UpstreamServiceException(
                    "No se pudo consultar el horario en el Portal UTP: " + lastFailure.getMessage(), lastFailure);
        }

        int rejected = rejectedCountRef.get();
        int unresolvedBuildings = unresolvedBuildingRef.get();
        // Cualquier fallo de red/GraphQL en alguna semana, además de los rechazos y
        // edificios sin resolver, obliga a declarar el horario como PARTIAL.
        boolean isPartial = dashboardUpstreamFailed || rejected > 0 || unresolvedBuildings > 0
                || lastFailure != null;
        if (isPartial) {
            log.warn("[UtpPortalGatewayAdapter] Horario enriquecido parcialmente: dashboardFailed={}, "
                    + "rejectedItems={}, unresolvedBuildings={}, weekFailure={}",
                    dashboardUpstreamFailed, rejected, unresolvedBuildings, lastFailure != null);
        }

        String effectivePeriodName = periodInfo.name();
        return ScheduleInterval.builder()
                .id("interval-graphql-" + currentWeekCalc)
                .periodName(effectivePeriodName)
                .weekNumber(currentWeekCalc)
                .totalWeeks(totalWeeks)
                .startDate(periodStartDate)
                .endDate(periodEndDate)
                .courses(new ArrayList<>(coursesMap.values()))
                .classes(allClasses)
                .enrichmentStatus(isPartial ? "PARTIAL" : "COMPLETE")
                .build();
    }

    private void handleParsedItem(ScheduleItemParsingResult result,
                                  Map<String, Course> sectionCatalog,
                                  List<ClassSession> allClasses,
                                  Map<String, Course> coursesMap,
                                  java.util.concurrent.atomic.AtomicInteger rejectedCount,
                                  java.util.concurrent.atomic.AtomicInteger unresolvedBuildings) {
        if (result instanceof ScheduleItemParsingResult.Session s) {
            ClassSession session = s.session();
            allClasses.add(session);
            if (session.getBuildingId() != null && session.getBuilding() == null) {
                unresolvedBuildings.incrementAndGet();
            }
            coursesMap.putIfAbsent(session.getCourseName(), Course.builder()
                    .id("c-" + (session.getCourseName() == null ? "" :
                            session.getCourseName().toLowerCase().replaceAll("[^a-z0-9]", "-")))
                    .code(session.getCourseCode())
                    .name(session.getCourseName())
                    .section(session.getSection())
                    .sectionId(session.getSectionId())
                    .modalityType(session.getModalityType())
                    .teacher(session.getTeacher())
                    .modality(session.getModality())
                    .build());
        } else if (result instanceof ScheduleItemParsingResult.AsyncCourseRef a) {
            Course ref = a.sectionId() != null ? sectionCatalog.get(a.sectionId()) : null;
            coursesMap.putIfAbsent(a.name(), Course.builder()
                    .id("c-" + (a.name() == null ? "" : a.name().toLowerCase().replaceAll("[^a-z0-9]", "-")))
                    .code(a.courseCode() != null ? a.courseCode() : (ref != null ? ref.getCode() : null))
                    .name(a.name())
                    .section(ref != null ? ref.getSection() : null)
                    .sectionId(a.sectionId())
                    .classLink(a.classLink())
                    .modalityType(ref != null ? ref.getModalityType() : com.utp.horario.domain.model.ModalityType.ASYNC_VIRTUAL)
                    .modality("Virtual 24/7")
                    .build());
        } else if (result instanceof ScheduleItemParsingResult.Rejected r) {
            log.warn("Sesión descartada por datos anómalos de UTP: {}", r.reason());
            rejectedCount.incrementAndGet();
        }
    }
    private void ensureBuildingsLoaded(String token, String studentCode) {
        if (!buildingCache.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> bodyObj = Map.of(
                    "operationName", "getBuildings",
                    "variables", Map.of(),
                    "query", "query getBuildings {\n  buildings {\n    id\n    description\n    address\n  }\n}\n");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(getPortalEndpoint()))
                    .timeout(Duration.ofSeconds(10))
                    .header("accept", "*/*")
                    .header("applicationid", "APP00002")
                    .header("authorization", "Bearer " + token)
                    .header("content-type", "application/json")
                    .header("isreservation", "false")
                    .header("user-id", studentCode)
                    .header("user-role", "student")
                    .header("Referer", "https://portal.utp.edu.pe/")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyObj)))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode bArray = objectMapper.readTree(resp.body()).path("data").path("buildings");
                if (bArray.isArray()) {
                    for (JsonNode b : bArray) {
                        String id = b.path("id").asText();
                        String desc = b.path("description").asText("");
                        if (desc.isBlank()) desc = b.path("address").asText("");
                        if (!id.isBlank() && !desc.isBlank()) {
                            buildingCache.put(id, desc);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Un fallo del catálogo no invalida el horario: los ids crudos se conservan
            // y enrichmentStatus se marca PARTIAL al detectar edificios no resueltos.
            log.warn("[UTP Portal GraphQL] No se pudo cargar catálogo de edificios: {}", e.getMessage());
        }
    }

    public String fetchSyllabusPdfText(String token, String courseCodeOrUrl) {
        if (courseCodeOrUrl == null || courseCodeOrUrl.isBlank()) {
            return null;
        }

        // Token efectivo: SOLO el propio de la petición. Nunca se presta el de otro estudiante.
        String effectiveToken = stripBearerPrefix(token);

        try {
            String pdfUrl = courseCodeOrUrl.trim();

            if (!pdfUrl.startsWith("http://") && !pdfUrl.startsWith("https://")) {
                // La resolución contra el portal PAO exige credenciales propias.
                // Sin token del solicitante no se invoca a un endpoint autenticado
                // (y jamás con el token de otro estudiante).
                if (effectiveToken.isBlank()) {
                    log.warn("[UtpPortalGatewayAdapter] Sílabo '{}' no resoluble: la petición no incluye credenciales propias", courseCodeOrUrl);
                    throw new AuthenticationException(
                            "Se requiere token de sesión institucional UTP para resolver el sílabo solicitado.");
                }

                String userId = extractUserIdFromToken(effectiveToken);
                String tenantId = extractTenantIdFromToken(effectiveToken);

                // Resolución directa desde el catálogo institucional: sin mapas de
                // referencias persistentes ni sondas de identidad.
                List<DashboardCourseRef> catalog = loadDashboardCourses(effectiveToken);
                String normalizedTarget = CourseNameNormalizer.normalize(pdfUrl);
                DashboardCourseRef matchedRef = null;

                for (DashboardCourseRef ref : catalog) {
                    String code = ref.courseCode() != null ? ref.courseCode() : "";
                    String name = ref.name() != null ? ref.name() : "";
                    String sId = ref.sectionId() != null ? ref.sectionId() : "";
                    String cNum = ref.classNumber() != null ? ref.classNumber() : "";
                    String sCode = ref.sectionCode() != null ? ref.sectionCode() : "";

                    if (pdfUrl.equalsIgnoreCase(sId)
                            || normalizedTarget.equals(code)
                            || normalizedTarget.equals(CourseNameNormalizer.normalize(name))
                            || pdfUrl.equals(cNum)
                            || pdfUrl.equalsIgnoreCase(sCode)) {
                        matchedRef = ref;
                        break;
                    }
                }

                String syllabusApiUrl = null;
                if (matchedRef != null && matchedRef.courseId() != null && !matchedRef.courseId().isBlank()
                        && matchedRef.sectionId() != null && !matchedRef.sectionId().isBlank()) {
                    // Checkpoint §4.4: /course/student/courses/{courseId}/sections/{sectionId}/syllabus
                    syllabusApiUrl = getPaoEndpoint("/course/student/courses/"
                            + URLEncoder.encode(matchedRef.courseId().trim(), StandardCharsets.UTF_8)
                            + "/sections/"
                            + URLEncoder.encode(matchedRef.sectionId().trim(), StandardCharsets.UTF_8)
                            + "/syllabus");
                } else if (matchedRef != null && matchedRef.classNumber() != null && !matchedRef.classNumber().isBlank()) {
                    syllabusApiUrl = getPaoEndpoint("/course/student/sections/"
                            + URLEncoder.encode(matchedRef.classNumber().trim(), StandardCharsets.UTF_8) + "/syllabus");
                } else if (pdfUrl.matches("^\\d+$")) {
                    syllabusApiUrl = getPaoEndpoint("/course/student/sections/"
                            + URLEncoder.encode(pdfUrl.trim(), StandardCharsets.UTF_8) + "/syllabus");
                }

                if (syllabusApiUrl != null) {
                    log.info("[UtpPortalGatewayAdapter] Consultando endpoint de sílabo PAO: {} (userId='{}')", syllabusApiUrl, userId);
                    HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                            .uri(URI.create(syllabusApiUrl))
                            .timeout(Duration.ofSeconds(10))
                            .header("accept", "*/*")
                            .header("x-tenant-id", tenantId)
                            .header("user-id", userId)
                            .header("user-id-to-access", "")
                            .header("user-role", "STUDENT")
                            .header("user-role-to-access", "")
                            .header("transaction-id", java.util.UUID.randomUUID().toString())
                            .header("origin", "https://class.utp.edu.pe")
                            .header("referer", "https://class.utp.edu.pe/");

                    if (effectiveToken != null && !effectiveToken.isBlank()) {
                        reqBuilder.header("authorization", effectiveToken.startsWith("Bearer ") ? effectiveToken : "Bearer " + effectiveToken);
                    }

                    HttpResponse<String> res = httpClient.send(reqBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

                    // Fallback de contingencia: si la ruta jerárquica canónica respondió 404 y se dispone de un classNumber numérico
                    if (res.statusCode() == 404 && matchedRef != null && matchedRef.classNumber() != null
                            && matchedRef.classNumber().matches("^\\d+$")
                            && syllabusApiUrl.contains("/courses/")) {
                        String fallbackUrl = getPaoEndpoint("/course/student/sections/"
                                + URLEncoder.encode(matchedRef.classNumber().trim(), StandardCharsets.UTF_8) + "/syllabus");
                        log.info("[UtpPortalGatewayAdapter] Intento con ruta de contingencia PAO: {}", fallbackUrl);
                        reqBuilder.uri(URI.create(fallbackUrl));
                        res = httpClient.send(reqBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());
                    }

                    if (res.statusCode() == 200) {
                        JsonNode node = objectMapper.readTree(res.body());
                        if (node.has("errors")) {
                            throw new UpstreamServiceException("PAO devolvió errores al resolver el sílabo de " + courseCodeOrUrl + ".");
                        }
                        if (node.path("data").hasNonNull("syllabusUrl")) {
                            pdfUrl = node.path("data").path("syllabusUrl").asText();
                            log.info("[UtpPortalGatewayAdapter] URL de sílabo obtenida desde PAO API: {}", pdfUrl);
                        } else {
                            throw new ResourceNotFoundException(
                                    "PAO no publicó una URL de sílabo para el curso solicitado: " + courseCodeOrUrl);
                        }
                    } else if (res.statusCode() == 401 || res.statusCode() == 403) {
                        log.warn("[UtpPortalGatewayAdapter] Descarga de sílabo rechazada por PAO (HTTP {})", res.statusCode());
                        throw new AuthenticationException(
                                "Sesión institucional UTP expirada o no autorizada para resolver el sílabo (HTTP " + res.statusCode() + ").");
                    } else if (res.statusCode() == 404) {
                        throw new ResourceNotFoundException(
                                "PAO no encontró el sílabo solicitado: " + courseCodeOrUrl);
                    } else {
                        log.warn("[UtpPortalGatewayAdapter] Respuesta no exitosa ({}) de PAO syllabus para {}", res.statusCode(), syllabusApiUrl);
                        throw new UpstreamServiceException(
                                "PAO respondió con error HTTP " + res.statusCode() + " al resolver el sílabo.");
                    }
                }
                }

            if (pdfUrl.startsWith("http://") || pdfUrl.startsWith("https://")) {
                String safeUri = pdfUrl.replace(" ", "%20");
                log.info("[UtpPortalGatewayAdapter] Descargando binario PDF desde: {}", safeUri);
                HttpRequest pdfReq = HttpRequest.newBuilder()
                        .uri(URI.create(safeUri))
                        .timeout(Duration.ofSeconds(8))
                        .GET()
                        .build();

                HttpResponse<byte[]> pdfRes = httpClient.send(pdfReq, HttpResponse.BodyHandlers.ofByteArray());
                if (pdfRes.statusCode() == 200 && pdfRes.body() != null && pdfRes.body().length > 0) {
                    try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdfRes.body())) {
                        org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                        String extracted = stripper.getText(document);
                        log.info("[UtpPortalGatewayAdapter] Texto de sílabo extraído exitosamente ({} caracteres)", extracted != null ? extracted.length() : 0);
                        return extracted;
                    }
                } else if (pdfRes.statusCode() == 401 || pdfRes.statusCode() == 403) {
                    log.warn("[UtpPortalGatewayAdapter] Descarga rechazada de PDF de S3, status: {}", pdfRes.statusCode());
                    throw new AuthenticationException("Sesión institucional UTP expirada o no autorizada para descargar sílabo (HTTP " + pdfRes.statusCode() + ").");
                } else if (pdfRes.statusCode() == 404) {
                    throw new ResourceNotFoundException("PDF de sílabo no encontrado en almacenamiento institucional para: " + courseCodeOrUrl);
                } else {
                    log.warn("[UtpPortalGatewayAdapter] Error descargando PDF de S3, status: {}", pdfRes.statusCode());
                    throw new UpstreamServiceException("Almacenamiento institucional UTP respondió con error HTTP " + pdfRes.statusCode() + " al descargar sílabo.");
                }
            }
        } catch (AuthenticationException | ResourceNotFoundException | UpstreamServiceException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[UtpPortalGatewayAdapter] Error descargando/extrayendo PDF de sílabo para '{}': {}", courseCodeOrUrl, e.getMessage());
            throw new UpstreamServiceException("Error al procesar PDF de sílabo: " + e.getMessage(), e);
        }

        throw new ResourceNotFoundException(
                "No se pudo resolver la URL del sílabo para: " + courseCodeOrUrl);
    }

    private final Map<String, String> sectionToCourseCodeCache = new ConcurrentHashMap<>();
    private final Map<String, String> courseNameToCourseCodeCache = new ConcurrentHashMap<>();

    /** Referencia institucional completa por sección, derivada del dashboard de UTP+ Class. */
    private record DashboardCourseRef(String courseId, String sectionId, String courseCode, String classNumber,
                                      String sectionCode, String name, ModalityType modality) {}

    /** Marcador para impedir que un fallo del catálogo se degrade a un horario vacío. */
    private static final class DashboardAuthException extends AuthenticationException {
        DashboardAuthException(String message) { super(message); }
    }

    private static final class DashboardNotFoundException extends ResourceNotFoundException {
        DashboardNotFoundException(String message) { super(message); }
    }

    private static final class DashboardUpstreamException extends UpstreamServiceException {
        DashboardUpstreamException(String message, Throwable cause) { super(message, cause); }
        DashboardUpstreamException(String message) { super(message); }
    }

    private static boolean isDashboardFailure(RuntimeException e) {
        return e instanceof DashboardAuthException
                || e instanceof DashboardNotFoundException
                || e instanceof DashboardUpstreamException;
    }

    /**
     * Modelo de código de catálogo institucional según variantes de la plataforma UTP:
     * - REGULAR: 100000 + 4 alfanuméricos (ej. 100000S72T, 100000EMP1)
     * - WORKSHOP: 10000TE + 3 dígitos (ej. 10000TE103)
     * - OTHER: 10 caracteres alfanuméricos canónicos
     */
    public record CatalogCode(String value, Kind kind) {
        public enum Kind { REGULAR, WORKSHOP, OTHER }

        /** Ruptura 1: 5 o 6 dígitos iniciales y 4-5 alfanuméricos (THP 10000TE103 incluido). */
        private static final Pattern REGULAR_PATTERN = Pattern.compile("^\\d{5,6}[A-Z0-9]{4,5}$");
        private static final Pattern WORKSHOP_PATTERN = Pattern.compile("^10000TE\\d{3}$");
        private static final Pattern GENERIC_PATTERN = Pattern.compile("^[A-Z0-9]{10}$");

        public static java.util.Optional<CatalogCode> parse(String raw) {
            if (raw == null) return java.util.Optional.empty();
            String v = raw.trim().toUpperCase(Locale.ROOT);
            if (REGULAR_PATTERN.matcher(v).matches()) return java.util.Optional.of(new CatalogCode(v, Kind.REGULAR));
            if (WORKSHOP_PATTERN.matcher(v).matches()) return java.util.Optional.of(new CatalogCode(v, Kind.WORKSHOP));
            if (GENERIC_PATTERN.matcher(v).matches()) return java.util.Optional.of(new CatalogCode(v, Kind.OTHER));
            return java.util.Optional.empty();
        }
    }

    private static final Pattern SECTION_EMBEDDED_PATTERN = Pattern.compile("(?i)(\\d{5,6}[A-Z0-9]{4,5})");

    private static String validCatalogCode(String candidate) {
        return CatalogCode.parse(candidate).map(CatalogCode::value).orElse(null);
    }

    /**
     * Carga el dashboard institucional de forma FAIL-FAST con aislamiento por ítem.
     * Los errores 401/403/404/5xx y de red se propagan como excepciones tipadas;
     * nunca se devuelve un catálogo simulado.
     */
    private List<DashboardCourseRef> loadDashboardCourses(String token) {
        return loadDashboardCourses(token, (Set<String>) null);
    }

    private List<DashboardCourseRef> loadDashboardCourses(String token, String targetPeriod) {
        Set<String> periods = null;
        if (targetPeriod != null && !targetPeriod.isBlank()) {
            periods = new LinkedHashSet<>();
            String normalized = resolveAcademicPeriod(token, targetPeriod.trim()).id();
            if (normalized != null) {
                periods.add(normalized);
            }
            periods.add("9999");
        }
        return loadDashboardCourses(token, periods);
    }

    private List<DashboardCourseRef> loadDashboardCourses(String token, Set<String> targetPeriods) {
        String effectiveToken = (token != null && token.startsWith("Bearer ")) ? token.substring(7).trim() : token;
        if (effectiveToken == null || effectiveToken.isBlank()) {
            throw new DashboardAuthException("Se requiere token de sesión UTP para resolver el catálogo de cursos.");
        }
        String userId = extractUserIdFromToken(effectiveToken);
        String tenantId = extractTenantIdFromToken(effectiveToken);
        String studentCode = extractStudentCodeFromToken(effectiveToken);
        String url = getPaoEndpoint("/learning/student/" + userId + "/dashboard-courses");

        HttpResponse<String> resp;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("accept", "*/*")
                    .header("authorization", "Bearer " + effectiveToken)
                    .header("user-id", userId)
                    .header("user-role", "STUDENT")
                    .header("x-tenant-id", tenantId)
                    .header("origin", "https://class.utp.edu.pe")
                    .header("referer", "https://class.utp.edu.pe/")
                    .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("transaction-id", java.util.UUID.randomUUID().toString())
                    .GET()
                    .build();
            resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new DashboardUpstreamException("Error de red al consultar dashboard-courses: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DashboardUpstreamException("Llamada interrumpida al consultar dashboard-courses.", e);
        }

        int status = resp.statusCode();
        if (status == 401 || status == 403) {
            throw new DashboardAuthException("Sesión UTP rechazada por UTP+ Class al consultar dashboard-courses (HTTP " + status + ").");
        }
        if (status == 404) {
            throw new DashboardNotFoundException("No se encontró el catálogo de cursos del estudiante en UTP+ Class.");
        }
        if (status != 200) {
            throw new DashboardUpstreamException("UTP+ Class respondió HTTP " + status + " al consultar dashboard-courses.");
        }

        try {
            JsonNode data = objectMapper.readTree(resp.body()).path("data");
            if (!data.isArray()) {
                throw new DashboardUpstreamException("El dashboard de UTP+ Class no devolvió un arreglo de cursos.");
            }

            int totalItems = data.size();
            int parsedCount = 0;
            int omittedCount = 0;
            List<DashboardCourseRef> refs = new ArrayList<>();

            for (JsonNode c : data) {
                String courseId = c.path("courseId").asText(null);
                String sectionId = c.path("sectionId").asText(null);
                String name = c.path("name").asText(null);
                try {
                    // Ruptura 5: conjunto compuesto {periodo actual, 9999}. Nunca se filtra
                    // por `active` (siempre true en Canvas PAO). Sin NPE/duplicados.
                    if (targetPeriods != null && !targetPeriods.isEmpty()) {
                        String coursePeriod = c.path("period").asText("");
                        if (coursePeriod.isBlank()) {
                            String sc = c.path("sectionCode").asText("");
                            if (sc.length() >= 4 && sc.substring(0, 4).matches("^\\d{4}$")) {
                                coursePeriod = sc.substring(0, 4);
                            }
                        }
                        if (!coursePeriod.isBlank() && !targetPeriods.contains(coursePeriod.trim())) {
                            continue;
                        }
                    }

                    String classNumber = c.path("classNumber").asText(null);
                    String sectionCode = c.path("sectionCode").asText("");
                    String courseCode = validCatalogCode(c.path("courseCode").asText(null));
                    if (courseCode == null) {
                        Matcher m = SECTION_EMBEDDED_PATTERN.matcher(sectionCode);
                        if (m.find()) {
                            courseCode = validCatalogCode(m.group(1));
                        }
                    }
                    ModalityType modality = ModalityType.from(c.path("modality").asText(null));
                    DashboardCourseRef ref = new DashboardCourseRef(courseId, sectionId, courseCode, classNumber, sectionCode, name, modality);
                    refs.add(ref);

                    // Prevención estricta de NPE en ConcurrentHashMap y aislamiento total multi-alumno
                    if (courseCode != null && !courseCode.isBlank()) {
                        parsedCount++;
                        if (sectionId != null && !sectionId.isBlank()) {
                            sectionToCourseCodeCache.put(sectionId, courseCode);
                        }
                        // Clave ESTRICTAMENTE acotada por estudiante: CERO clave global para evitar contaminación de mallas
                        if (name != null && !name.isBlank() && !studentCode.isBlank()) {
                            String norm = CourseNameNormalizer.normalize(name);
                            courseNameToCourseCodeCache.put(studentCode + ":" + norm, courseCode);
                        }
                    } else {
                        omittedCount++;
                    }
                } catch (RuntimeException itemEx) {
                    omittedCount++;
                    log.warn("[UtpPortalGatewayAdapter] Curso malformado omitido en dashboard-courses (sectionId={}, name={}): {}",
                            sectionId, name, itemEx.toString());
                }
            }

            if (totalItems > 0 && parsedCount == 0) {
                log.error("[UtpPortalGatewayAdapter] ALERTA CRITICA: Ningún curso de los {} presentes en dashboard-courses tuvo código de catálogo resoluble.", totalItems);
            } else if (omittedCount > 0) {
                log.warn("[UtpPortalGatewayAdapter] dashboard-courses: {} cursos sin código de catálogo resoluble de un total de {}", omittedCount, totalItems);
            }

            return refs;
        } catch (RuntimeException e) {
            if (isDashboardFailure(e)) throw e;
            throw new DashboardUpstreamException("Error al procesar dashboard-courses: " + e, e);
        } catch (Exception e) {
            throw new DashboardUpstreamException("Error al procesar dashboard-courses: " + e, e);
        }
    }

    public String resolveCourseCode(String sectionId, String courseName, String token) {
        if (sectionId != null && !sectionId.isBlank() && sectionToCourseCodeCache.containsKey(sectionId)) {
            return sectionToCourseCodeCache.get(sectionId);
        }
        String studentCode = (token != null) ? extractStudentCodeFromToken(token) : "";
        if (courseName != null && !courseName.isBlank() && !studentCode.isBlank()) {
            String norm = CourseNameNormalizer.normalize(courseName);
            String scopedKey = studentCode + ":" + norm;
            if (courseNameToCourseCodeCache.containsKey(scopedKey)) {
                return courseNameToCourseCodeCache.get(scopedKey);
            }
        }

        // Si no está en caché y tenemos token, consultar dashboard-courses de Class
        if (token != null && !token.isBlank()) {
            fetchAndCacheDashboardCourses(token);
            if (sectionId != null && !sectionId.isBlank() && sectionToCourseCodeCache.containsKey(sectionId)) {
                return sectionToCourseCodeCache.get(sectionId);
            }
            if (courseName != null && !courseName.isBlank() && !studentCode.isBlank()) {
                String norm = CourseNameNormalizer.normalize(courseName);
                String scopedKey = studentCode + ":" + norm;
                if (courseNameToCourseCodeCache.containsKey(scopedKey)) {
                    return courseNameToCourseCodeCache.get(scopedKey);
                }
            }
        }

        return null;
    }

    /** Vía legada (sílabos): mantiene la degradación silenciosa histórica sin afectar al horario. */
    private void fetchAndCacheDashboardCourses(String token) {
        try {
            loadDashboardCourses(token);
        } catch (Exception e) {
            log.warn("[UtpPortalGatewayAdapter] No se pudo cargar dashboard-courses: {}", e.getMessage());
        }
    }

    @Override
    public JsonNode fetchSectionFull(String sectionId, String token) {
        if (sectionId == null || sectionId.isBlank()) {
            throw new IllegalArgumentException("Se requiere sectionId para consultar el árbol de la sección.");
        }
        String effectiveToken = (token != null && token.startsWith("Bearer ")) ? token.substring(7).trim() : token;
        if (effectiveToken == null || effectiveToken.isBlank()) {
            throw new AuthenticationException("Se requiere token de sesión institucional UTP para consultar la sección.");
        }
        String userId = extractUserIdFromToken(effectiveToken);
        String tenantId = extractTenantIdFromToken(effectiveToken);
        String url = getPaoEndpoint("/course/student/sections/"
                + URLEncoder.encode(sectionId.trim(), StandardCharsets.UTF_8) + "/full");
        log.info("[UtpPortalGatewayAdapter] Consultando árbol completo de la sección en: {}", url);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json, */*")
                    .header("Authorization", "Bearer " + effectiveToken)
                    .header("user-id", userId)
                    .header("user-role", "STUDENT")
                    .header("x-tenant-id", tenantId)
                    .header("transaction-id", java.util.UUID.randomUUID().toString())
                    .header("origin", "https://class.utp.edu.pe")
                    .header("referer", "https://class.utp.edu.pe/")
                    .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                if (root == null || root.isNull()) {
                    throw new UpstreamServiceException("UTP PAO devolvió un cuerpo vacío al consultar la sección " + sectionId + ".");
                }
                if (root.has("errors")) {
                    throw new UpstreamServiceException("UTP PAO devolvió errores al consultar la sección " + sectionId + ".");
                }
                return root.hasNonNull("data") ? root.get("data") : root;
            }
            if (status == 401 || status == 403) {
                throw new AuthenticationException("Token institucional UTP inválido o expirado para consultar sección " + sectionId);
            }
            if (status == 404) {
                throw new ResourceNotFoundException("Sección académica " + sectionId + " no encontrada en UTP+ Class.");
            }
            throw new UpstreamServiceException("UTP PAO respondió con código " + status + " al consultar sección " + sectionId);
        } catch (AuthenticationException | ResourceNotFoundException | UpstreamServiceException e) {
            throw e;
        } catch (IOException e) {
            throw new UpstreamServiceException("Error de red al consultar la sección " + sectionId + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamServiceException("Llamada interrumpida al consultar la sección " + sectionId + ".", e);
        } catch (Exception e) {
            throw new UpstreamServiceException("Error al procesar el árbol de la sección " + sectionId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public SectionCurriculumTree fetchSectionCurriculum(String sectionId, String token) {
        String effectiveToken = (token != null && token.startsWith("Bearer ")) ? token.substring(7).trim() : token;
        JsonNode root = fetchSectionFull(sectionId, effectiveToken);
        return parseSectionTree(sectionId, effectiveToken, root);
    }

    public SectionCurriculumTree parseSectionTree(String sectionId, String token, JsonNode node) {
        if (node == null || node.isNull()) {
            return SectionCurriculumTree.builder().sectionId(sectionId).units(List.of()).build();
        }

        String courseCode = resolveCourseCode(sectionId, null, token);

        JsonNode unitsArray = null;
        if (node.isArray()) {
            unitsArray = node;
        } else if (node.has("units") && node.get("units").isArray()) {
            unitsArray = node.get("units");
        } else if (node.has("unities") && node.get("unities").isArray()) {
            unitsArray = node.get("unities");
        }

        List<SectionUnit> units = new ArrayList<>();
        if (unitsArray != null) {
            int unitIdx = 1;
            for (JsonNode uNode : unitsArray) {
                int unitNum = uNode.hasNonNull("unitNumber") ? uNode.get("unitNumber").asInt()
                        : (uNode.hasNonNull("number") ? uNode.get("number").asInt() : unitIdx++);
                String unitTitle = uNode.hasNonNull("title") ? uNode.get("title").asText()
                        : (uNode.hasNonNull("name") ? uNode.get("name").asText("Unidad " + unitNum) : "Unidad " + unitNum);

                JsonNode weeksArray = null;
                if (uNode.has("weeks") && uNode.get("weeks").isArray()) {
                    weeksArray = uNode.get("weeks");
                } else if (uNode.has("themes") && uNode.get("themes").isArray()) {
                    weeksArray = uNode.get("themes");
                }

                List<SectionWeek> weeks = new ArrayList<>();
                if (weeksArray != null) {
                    int weekIdx = 1;
                    for (JsonNode wNode : weeksArray) {
                        int weekNum = wNode.hasNonNull("weekNumber") ? wNode.get("weekNumber").asInt()
                                : (wNode.hasNonNull("number") ? wNode.get("number").asInt() : weekIdx++);
                        String weekName = wNode.hasNonNull("name") ? wNode.get("name").asText()
                                : (wNode.hasNonNull("title") ? wNode.get("title").asText("Semana " + weekNum) : "Semana " + weekNum);

                        JsonNode topicsArray = null;
                        if (wNode.has("topics") && wNode.get("topics").isArray()) {
                            topicsArray = wNode.get("topics");
                        } else if (wNode.has("contents") && wNode.get("contents").isArray()) {
                            topicsArray = wNode.get("contents");
                        }

                        List<SectionTopic> topics = new ArrayList<>();
                        if (topicsArray != null) {
                            for (JsonNode tNode : topicsArray) {
                                String topicId = tNode.hasNonNull("id") ? tNode.get("id").asText()
                                        : (tNode.hasNonNull("topicId") ? tNode.get("topicId").asText(null) : null);
                                String topicName = tNode.hasNonNull("name") ? tNode.get("name").asText()
                                        : (tNode.hasNonNull("title") ? tNode.get("title").asText("Tema") : "Tema");

                                List<SectionMaterial> materials = new ArrayList<>();
                                if (tNode.has("materials") && tNode.get("materials").isArray()) {
                                    for (JsonNode mNode : tNode.get("materials")) {
                                        materials.add(SectionMaterial.builder()
                                                .id(mNode.hasNonNull("id") ? mNode.get("id").asText() : mNode.path("materialId").asText(null))
                                                .title(mNode.path("title").asText(mNode.path("name").asText("Material")))
                                                .type(mNode.path("type").asText("DOCUMENT"))
                                                .url(mNode.path("url").asText(null))
                                                .viewed(mNode.hasNonNull("viewed") ? mNode.get("viewed").asBoolean() : null)
                                                .build());
                                    }
                                } else if (tNode.has("url") && !tNode.path("url").asText("").isBlank()) {
                                    materials.add(SectionMaterial.builder()
                                            .id(topicId)
                                            .title(topicName)
                                            .type(tNode.path("type").asText("DOCUMENT"))
                                            .url(tNode.path("url").asText(null))
                                            .viewed(tNode.hasNonNull("viewed") ? tNode.get("viewed").asBoolean() : null)
                                            .build());
                                }

                                topics.add(SectionTopic.builder()
                                        .id(topicId)
                                        .name(topicName)
                                        .materials(materials)
                                        .build());
                            }
                        }

                        weeks.add(SectionWeek.builder()
                                .weekNumber(weekNum)
                                .name(weekName)
                                .topics(topics)
                                .build());
                    }
                }

                units.add(SectionUnit.builder()
                        .unitNumber(unitNum)
                        .title(unitTitle)
                        .weeks(weeks)
                        .build());
            }
        }

        Integer numWeeks = node.hasNonNull("numWeeks") ? node.get("numWeeks").asInt() : null;
        Integer currentWeek = node.hasNonNull("currentWeek") ? node.get("currentWeek").asInt() : null;
        String courseId = node.hasNonNull("courseId") ? node.get("courseId").asText() : null;

        return SectionCurriculumTree.builder()
                .sectionId(sectionId)
                .courseId(courseId)
                .courseCode(courseCode)
                .numWeeks(numWeeks)
                .currentWeek(currentWeek)
                .units(units)
                .build();
    }

    @Override
    public TaskSpecification fetchTaskSpecification(String sectionId, String activityId, String token) {
        String effectiveToken = (token != null && token.startsWith("Bearer ")) ? token.substring(7).trim() : token;
        if (effectiveToken == null || effectiveToken.isBlank()) {
            throw new AuthenticationException("Se requiere token de sesión institucional UTP para consultar la tarea.");
        }
        String userId = extractUserIdFromToken(effectiveToken);
        String tenantId = extractTenantIdFromToken(effectiveToken);

        String url = getPaoEndpoint("/course/student/sections/" + sectionId + "/homeworks/" + activityId + "/resume");
        log.info("[UtpPortalGatewayAdapter] Consultando especificación de tarea en: {}", url);

        String resolvedCourseCode = resolveCourseCode(sectionId, null, effectiveToken);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("accept", "*/*")
                    .header("authorization", "Bearer " + effectiveToken)
                    .header("user-id", userId)
                    .header("user-role", "STUDENT")
                    .header("x-tenant-id", tenantId)
                    .header("transaction-id", java.util.UUID.randomUUID().toString())
                    .header("origin", "https://class.utp.edu.pe")
                    .header("referer", "https://class.utp.edu.pe/")
                    .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                if (root.has("errors")) {
                    throw new UpstreamServiceException("UTP PAO devolvió errores al consultar la especificación de la tarea " + activityId + ".");
                }
                JsonNode data = root.path("data");
                if (data.isMissingNode() || data.isNull() || !data.isObject()) {
                    throw new UpstreamServiceException("UTP PAO no devolvió datos para la tarea " + activityId + ".");
                }
                String title = data.path("title").asText("").trim();
                if (title.isBlank()) {
                    throw new UpstreamServiceException("UTP PAO devolvió una tarea sin título para " + activityId + ".");
                }

                String rawContent = data.path("content").asText("");
                String rawDeliverables = data.path("deliverables").asText("");
                String markdownDesc = convertHtmlToMarkdown(rawContent);
                String markdownDeliverables = convertHtmlToMarkdown(rawDeliverables);

                List<String> submissionTypes = new ArrayList<>();
                String combinedText = (rawContent + " " + rawDeliverables).toLowerCase();
                if (combinedText.contains("video") || combinedText.contains("youtube") || combinedText.contains("enlace") || combinedText.contains("url")) {
                    submissionTypes.add("online_url");
                }
                if (combinedText.contains("pdf") || combinedText.contains("archivo") || combinedText.contains("sube tu") || combinedText.contains("documento")) {
                    submissionTypes.add("online_upload");
                }
                if (submissionTypes.isEmpty()) {
                    submissionTypes.add("online_upload");
                }

                List<RubricCriterion> rubricCriterions = new ArrayList<>();
                String rubricName = null;
                Double rubricScore = null;

                if (data.hasNonNull("rubric") && data.path("rubric").isObject()) {
                    JsonNode rNode = data.path("rubric");
                    rubricName = rNode.path("name").asText(null);
                    rubricScore = rNode.hasNonNull("score") ? rNode.path("score").asDouble() : null;

                    if (rNode.has("criterions") && rNode.path("criterions").isArray()) {
                        for (JsonNode cNode : rNode.path("criterions")) {
                            List<RubricLevel> levels = new ArrayList<>();
                            if (cNode.has("performanceRatings") && cNode.path("performanceRatings").isArray()) {
                                for (JsonNode prNode : cNode.path("performanceRatings")) {
                                    levels.add(RubricLevel.builder()
                                            .id(prNode.path("id").asText(null))
                                            .name(prNode.path("name").asText(""))
                                            .score(prNode.path("score").asDouble(0.0))
                                            .description(prNode.path("description").asText(""))
                                            .order(prNode.path("order").asInt(0))
                                            .build());
                                }
                            }

                            String desc = cNode.path("description").asText("").trim();
                            if (desc.isEmpty() && !levels.isEmpty()) {
                                desc = levels.get(0).getDescription();
                            }

                            rubricCriterions.add(RubricCriterion.builder()
                                    .id(cNode.path("id").asText(null))
                                    .name(cNode.path("name").asText("Criterio general"))
                                    .score(cNode.path("score").asDouble(0.0))
                                    .description(desc)
                                    .order(cNode.path("order").asInt(0))
                                    .performanceRatings(levels)
                                    .build());
                        }
                    }
                }

                if ((resolvedCourseCode == null || resolvedCourseCode.isBlank()) && rubricName != null) {
                    Matcher m = Pattern.compile("(?i)^([A-Z0-9]{4})-").matcher(rubricName);
                    if (m.find()) {
                        resolvedCourseCode = "100000" + m.group(1).toUpperCase();
                    }
                }

                Integer attempts = data.hasNonNull("attempts") ? data.path("attempts").asInt() : 1;

                List<TaskAttachment> attachments = new ArrayList<>();
                if (data.has("files") && data.path("files").isArray()) {
                    for (JsonNode f : data.path("files")) {
                        attachments.add(TaskAttachment.builder()
                                .id(f.path("id").asText(null))
                                .name(f.path("name").asText(f.path("filename").asText("Adjunto")))
                                .url(f.path("url").asText(null))
                                .type(f.path("type").asText(null))
                                .size(f.hasNonNull("size") ? f.path("size").asLong() : null)
                                .build());
                    }
                }

                boolean rubricAvailable = !rubricCriterions.isEmpty();

                return TaskSpecification.builder()
                        .id(data.path("id").asText(activityId))
                        .title(title)
                        .courseCode(resolvedCourseCode)
                        .sectionId(sectionId)
                        .descriptionMarkdown(markdownDesc)
                        .deliverablesMarkdown(markdownDeliverables)
                        .maxAttempts(attempts)
                        .submissionTypes(submissionTypes)
                        .availableFrom(data.path("availableFrom").asText(null))
                        .availableUntil(data.path("availableUntil").asText(null))
                        .dueAt(data.path("availableUntil").asText(null))
                        .unlockAt(data.path("availableFrom").asText(null))
                        .lockAt(data.path("availableUntil").asText(null))
                        .evaluationSystem(data.path("evaluationSystem").asText(null))
                        .isGroup(data.path("isGroup").asBoolean(false))
                        .homeworkStatus(data.path("homeworkStatus").asText("PENDING"))
                        .evaluationTopScore(data.path("evaluationTopScore").asDouble(20.0))
                        .rubricName(rubricName)
                        .rubricScore(rubricScore)
                        .gradingRubric(rubricCriterions)
                        .rubricAvailable(rubricAvailable)
                        .attachments(attachments)
                        .build();
            } else if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                log.warn("[UtpPortalGatewayAdapter] Error de autenticación {} al obtener tarea {}", resp.statusCode(), activityId);
                throw new AuthenticationException("Sesión institucional UTP expirada o no autorizada para consultar la tarea (HTTP " + resp.statusCode() + ").");
            } else if (resp.statusCode() == 404) {
                throw new ResourceNotFoundException("Tarea no encontrada en el portal UTP: " + activityId);
            } else {
                log.warn("[UtpPortalGatewayAdapter] Error {} al obtener tarea {}: {}", resp.statusCode(), activityId, resp.body());
                throw new UpstreamServiceException("Error HTTP " + resp.statusCode() + " al consultar especificación de tarea en UTP.");
            }
        } catch (AuthenticationException | ResourceNotFoundException | UpstreamServiceException re) {
            throw re;
        } catch (Exception e) {
            log.error("[UtpPortalGatewayAdapter] Excepción al consultar homework resume {}: {}", activityId, e.getMessage());
            throw new UpstreamServiceException("Error de conexión al consultar tarea en UTP: " + e.getMessage(), e);
        }
    }

    @Override
    public List<AcademicActivity> fetchCalendarActivities(String dateToQuery, String intervalMode, String token) {
        String effectiveToken = (token != null && token.startsWith("Bearer ")) ? token.substring(7).trim() : token;
        if (effectiveToken == null || effectiveToken.isBlank()) {
            throw new AuthenticationException("Se requiere token de sesión institucional UTP para consultar el calendario.");
        }
        String userId = extractUserIdFromToken(effectiveToken);
        String tenantId = extractTenantIdFromToken(effectiveToken);

        String effectiveDate = (dateToQuery != null && !dateToQuery.isBlank())
                ? dateToQuery
                : LocalDate.now().toString() + "+00:00:00";
        String effectiveMode = (intervalMode != null && !intervalMode.isBlank()) ? intervalMode : "period";

        String url = getPaoEndpoint("/course/student/calendar/activities?userId=" + userId)
                + "&dateToQuery=" + effectiveDate + "&intervalMode=" + effectiveMode;

        log.info("[UtpPortalGatewayAdapter] Consultando calendario de actividades en: {}", url);
        List<AcademicActivity> activities = new ArrayList<>();

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("accept", "*/*")
                    .header("authorization", "Bearer " + effectiveToken)
                    .header("user-id", userId)
                    .header("user-role", "STUDENT")
                    .header("x-tenant-id", tenantId)
                    .header("transaction-id", java.util.UUID.randomUUID().toString())
                    .header("origin", "https://class.utp.edu.pe")
                    .header("referer", "https://class.utp.edu.pe/")
                    .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                if (root.has("errors")) {
                    throw new UpstreamServiceException("UTP PAO devolvió errores al consultar el calendario de actividades.");
                }
                JsonNode eventsNode = root.path("data").path("current_interval").path("events");
                if (eventsNode.isArray()) {
                    for (JsonNode ev : eventsNode) {
                        JsonNode meta = ev.path("metadata");
                        String title = ev.path("title").asText("");
                        String type = meta.path("activityType").asText(ev.path("type").asText("ACTIVITY"));
                        String finishAt = ev.path("finishAt").asText(null);
                        String evalSystem = meta.path("evaluationSystem").asText(null);
                        boolean isQualified = meta.path("isQualified").asBoolean(false);

                        String category = calculateCategory(title, type, evalSystem, isQualified);
                        UrgencyInfo urgencyInfo = calculateUrgency(finishAt);

                        String secId = meta.path("sectionId").asText(null);
                        String cName = meta.path("courseName").asText(null);
                        String cCode = resolveCourseCode(secId, null, effectiveToken);

                        activities.add(AcademicActivity.builder()
                                .id(ev.path("id").asText(null))
                                .title(title)
                                .activityType(type)
                                .weekNumber(meta.path("weekNumber").asInt(0))
                                .startAt(ev.path("startAt").asText(null))
                                .finishAt(finishAt)
                                .courseCode(cCode)
                                .courseName(cName)
                                .courseId(meta.path("courseId").asText(null))
                                .sectionId(secId)
                                .contentId(meta.path("contentId").asText(null))
                                .activityId(meta.path("activityId").asText(null))
                                .evaluationSystem(evalSystem)
                                .studentStatus(meta.path("studentStatus").asText("PENDING"))
                                .isQualified(isQualified)
                                .classificationCategory(category)
                                .urgency(urgencyInfo.urgency())
                                .daysRemaining(urgencyInfo.daysRemaining())
                                .build());
                    }
                } else {
                    throw new UpstreamServiceException("UTP PAO no devolvió un arreglo de eventos para el calendario de actividades.");
                }
            } else if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                log.warn("[UtpPortalGatewayAdapter] Error {} consultando calendar activities: sesión no autorizada o expirada", resp.statusCode());
                throw new AuthenticationException("Sesión institucional UTP expirada o no autorizada en PAO (HTTP " + resp.statusCode() + ").");
            } else if (resp.statusCode() == 404) {
                throw new ResourceNotFoundException("No se encontró el calendario de actividades del estudiante en PAO.");
            } else if (resp.statusCode() >= 500) {
                log.error("[UtpPortalGatewayAdapter] Error {} consultando calendar activities en PAO", resp.statusCode());
                throw new UpstreamServiceException("El servicio de calendario PAO de UTP respondió con error HTTP " + resp.statusCode() + ".");
            } else {
                log.warn("[UtpPortalGatewayAdapter] Error {} consultando calendar activities", resp.statusCode());
                throw new UpstreamServiceException("Error HTTP " + resp.statusCode() + " al consultar calendario de actividades UTP.");
            }
        } catch (AuthenticationException | ResourceNotFoundException | UpstreamServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("[UtpPortalGatewayAdapter] Excepción en fetchCalendarActivities: {}", e.getMessage());
            throw new UpstreamServiceException("Error de conexión al consultar actividades de calendario UTP: " + e.getMessage(), e);
        }

        return activities;
    }

    public record UrgencyInfo(String urgency, Long daysRemaining) {}

    private String calculateCategory(String title, String type, String evalSystem, boolean isQualified) {
        if ((evalSystem != null && !evalSystem.isBlank()) || isQualified || (title != null && EVAL_PATTERN.matcher(title).find())) {
            return "WEIGHTED_EVALUATION";
        }
        if (type != null) {
            String upper = type.toUpperCase();
            if (upper.contains("FORUM")) return "PARTICIPATION_FORUM";
            if (upper.contains("HOMEWORK") || upper.contains("TASK")) return "PRACTICE_HOMEWORK";
            if (upper.contains("EXAM")) return "EXAM";
        }
        return "GENERAL_ACTIVITY";
    }

    private UrgencyInfo calculateUrgency(String finishAtStr) {
        if (finishAtStr == null || finishAtStr.isBlank()) {
            return new UrgencyInfo("UNKNOWN", null);
        }
        try {
            LocalDateTime finish = LocalDateTime.parse(finishAtStr.trim(), java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            LocalDateTime now = LocalDateTime.now();
            long days = Duration.between(now, finish).toDays();
            if (finish.isBefore(now)) {
                return new UrgencyInfo("OVERDUE", days);
            }
            if (finish.toLocalDate().isEqual(now.toLocalDate())) {
                return new UrgencyInfo("DUE_TODAY", 0L);
            }
            if (finish.isBefore(now.plusDays(7))) {
                return new UrgencyInfo("DUE_THIS_WEEK", Math.max(0, days));
            }
            return new UrgencyInfo("UPCOMING", days);
        } catch (Exception e) {
            return new UrgencyInfo("UNKNOWN", null);
        }
    }

    @Override
    public CourseSummaryData fetchCourseSummary(String periodId, String token) {
        String effectiveToken = (token != null && token.startsWith("Bearer ")) ? token.substring(7).trim() : token;
        if (effectiveToken == null || effectiveToken.isBlank()) {
            throw new AuthenticationException("Se requiere token de sesión institucional UTP para consultar el resumen de cursos.");
        }
        String studentCode = extractStudentCodeFromToken(effectiveToken);
        if (studentCode == null || studentCode.isBlank()) {
            throw new AuthenticationException("Token de autenticación no contiene código de estudiante válido");
        }
        String effectivePeriod = (periodId != null && !periodId.isBlank())
                ? resolveAcademicPeriod(effectiveToken, periodId.trim()).id()
                : resolveCurrentAcademicPeriod(effectiveToken).id();

        String url = getPortalEndpoint();
        log.info("[UtpPortalGatewayAdapter] Consultando GetCourseSummary en Portal GraphQL para periodo: {}", effectivePeriod);

        Map<String, Object> bodyObj = new HashMap<>();
        bodyObj.put("operationName", "GetCourseSummary");
        bodyObj.put("variables", Map.of("periodId", effectivePeriod));
        bodyObj.put("query", "query GetCourseSummary($periodId: String!) {\n" +
                "  getCourseSummary(periodId: $periodId) {\n" +
                "    summary {\n" +
                "      campus\n" +
                "      enrolledCourses\n" +
                "      average\n" +
                "      relativeCycle\n" +
                "      creditCount\n" +
                "      meritOrder\n" +
                "      weeklyHours\n" +
                "      meritBelong\n" +
                "    }\n" +
                "    courses {\n" +
                "      courseId\n" +
                "      title\n" +
                "      catalogNumber\n" +
                "      section\n" +
                "      credits\n" +
                "      weeklyHours\n" +
                "      approvalStatus\n" +
                "      professors\n" +
                "      formula\n" +
                "      teacher\n" +
                "      average\n" +
                "      evaluations {\n" +
                "        name\n" +
                "        shortName\n" +
                "        value\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n");

        List<CourseSummaryData.CourseGradeItem> courses = new ArrayList<>();
        CourseSummaryData.PeriodSummary periodSummary = null;

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("accept", "*/*")
                    .header("content-type", "application/json")
                    .header("authorization", "Bearer " + effectiveToken)
                    .header("applicationid", "APP00002")
                    .header("isreservation", "false")
                    .header("user-id", studentCode)
                    .header("user-role", "student")
                    .header("origin", "https://portal.utp.edu.pe")
                    .header("referer", "https://portal.utp.edu.pe/")
                    .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(bodyObj)))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                if (root.has("errors")) {
                    throw new UpstreamServiceException("El Portal UTP devolvió errores al consultar el resumen de cursos.");
                }
                JsonNode summaryRoot = root.path("data").path("getCourseSummary");
                if (summaryRoot.isMissingNode() || summaryRoot.isNull() || !summaryRoot.isObject()) {
                    throw new UpstreamServiceException("El Portal UTP no devolvió datos de resumen de cursos.");
                }
                JsonNode summaryNode = summaryRoot.path("summary");
                if (summaryNode.isMissingNode() || summaryNode.isNull() || !summaryNode.isObject()) {
                    throw new UpstreamServiceException("El Portal UTP no devolvió el resumen del periodo.");
                }
                periodSummary = CourseSummaryData.PeriodSummary.builder()
                        .campus(summaryNode.path("campus").asText(null))
                        .enrolledCourses(summaryNode.path("enrolledCourses").asText(null))
                        .average(summaryNode.path("average").asText(null))
                        .relativeCycle(summaryNode.path("relativeCycle").asText(null))
                        .creditCount(summaryNode.path("creditCount").asText(null))
                        .meritOrder(summaryNode.path("meritOrder").asText(null))
                        .weeklyHours(summaryNode.path("weeklyHours").asText(null))
                        .meritBelong(summaryNode.path("meritBelong").asText(null))
                        .build();

                JsonNode coursesNode = summaryRoot.path("courses");
                if (!coursesNode.isArray()) {
                    throw new UpstreamServiceException("El Portal UTP no devolvió el arreglo de cursos del periodo.");
                }
                for (JsonNode cNode : coursesNode) {
                        List<CourseSummaryData.EvaluationGrade> evGrades = new ArrayList<>();
                        if (cNode.has("evaluations") && cNode.path("evaluations").isArray()) {
                            for (JsonNode ev : cNode.path("evaluations")) {
                                String val = ev.path("value").asText("");
                                String trimmedVal = val != null ? val.trim() : "";
                                // "0.02" es el centinela institucional de nota pendiente.
                                // 0.0 y 1.0 son notas reales y deben computarse.
                                boolean isGraded = false;
                                if (!trimmedVal.isEmpty() && !"0.02".equals(trimmedVal)) {
                                    try {
                                        Double.parseDouble(trimmedVal);
                                        isGraded = true;
                                    } catch (NumberFormatException ignored) {
                                        // Valor no numérico: pendiente.
                                    }
                                }

                                evGrades.add(CourseSummaryData.EvaluationGrade.builder()
                                        .name(ev.path("name").asText(""))
                                        .shortName(ev.path("shortName").asText(""))
                                        .value(val)
                                        .isGraded(isGraded)
                                        .build());
                            }
                        }

                        courses.add(CourseSummaryData.CourseGradeItem.builder()
                                .courseId(cNode.path("courseId").asText(""))
                                .courseCode(cNode.path("catalogNumber").asText(""))
                                .courseName(cNode.path("title").asText(""))
                                .section(cNode.path("section").asText(""))
                                .credits(cNode.path("credits").asText(""))
                                .formula(cNode.path("formula").asText(""))
                                .teacher(cNode.path("teacher").asText(""))
                                .average(cNode.path("average").asText(""))
                                .approvalStatus(cNode.path("approvalStatus").asText(null))
                                .professors(cNode.path("professors").asText(null))
                                .evaluations(evGrades)
                                .build());
                }
            } else if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                log.warn("[UtpPortalGatewayAdapter] Error {} en GetCourseSummary: sesión expirada o no autorizada", resp.statusCode());
                throw new AuthenticationException("Sesión institucional UTP expirada o no autorizada en Portal UTP (HTTP " + resp.statusCode() + ").");
            } else if (resp.statusCode() == 404) {
                throw new ResourceNotFoundException("No se encontró el resumen de cursos del estudiante en el Portal UTP.");
            } else if (resp.statusCode() >= 500) {
                log.error("[UtpPortalGatewayAdapter] Error {} en GetCourseSummary: fallo upstream", resp.statusCode());
                throw new UpstreamServiceException("El portal institucional UTP no está disponible (HTTP " + resp.statusCode() + ").");
            } else {
                log.warn("[UtpPortalGatewayAdapter] Error {} en GetCourseSummary", resp.statusCode());
                throw new UpstreamServiceException("Error HTTP " + resp.statusCode() + " al consultar resumen de cursos UTP.");
            }
        } catch (AuthenticationException | ResourceNotFoundException | UpstreamServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("[UtpPortalGatewayAdapter] Excepción en fetchCourseSummary: {}", e.getMessage());
            throw new UpstreamServiceException("Error de conexión al consultar resumen de cursos UTP: " + e.getMessage(), e);
        }

        return CourseSummaryData.builder()
                .periodId(effectivePeriod)
                .summary(periodSummary)
                .courses(courses)
                .build();
    }

    private String convertHtmlToMarkdown(String html) {
        if (html == null || html.isBlank()) return "";
        return html
                .replaceAll("(?i)<h[1-6][^>]*>(.*?)</h[1-6]>", "\n### $1\n")
                .replaceAll("(?i)<li[^>]*>(.*?)</li>", "- $1\n")
                .replaceAll("(?i)<p[^>]*>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)<strong[^>]*>(.*?)</strong>", "**$1**")
                .replaceAll("(?i)<b[^>]*>(.*?)</b>", "**$1**")
                .replaceAll("(?i)<em[^>]*>(.*?)</em>", "*$1*")
                .replaceAll("(?i)<i[^>]*>(.*?)</i>", "*$1*")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("<[^>]+>", "")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}

