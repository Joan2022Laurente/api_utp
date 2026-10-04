package com.utp.horario.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.model.AcademicActivity;
import com.utp.horario.domain.model.ClassSession;
import com.utp.horario.domain.model.Course;
import com.utp.horario.domain.model.CourseSummaryData;
import com.utp.horario.domain.model.RubricCriterion;
import com.utp.horario.domain.model.RubricLevel;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.model.StudentProfile;
import com.utp.horario.domain.model.TaskSpecification;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class UtpPortalGatewayAdapter implements UtpPortalGatewayPort {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, String> buildingCache = new ConcurrentHashMap<>();
    private final Map<String, String> studentPaoUserMap = new ConcurrentHashMap<>();
    private volatile String lastActiveToken = "";
    private static final Pattern EVAL_PATTERN = Pattern.compile("(?i)(PC\\d*|AP\\d*|EC\\d*|TI\\d*|EP|EF|EXFN|EXPA|PROY|AVANCE|PORTAFOLIO|PARTICIPACI[OÓ]N|EXAMEN)");

    public UtpPortalGatewayAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.studentPaoUserMap.put("u23307609", "4e535263-79a0-5890-ae33-72a7aa0629ab");
    }

    public static class ClassroomLocation {
        public final String aula;
        public final String pabellon;
        public final String campus;

        public ClassroomLocation(String aula, String pabellon, String campus) {
            this.aula = aula;
            this.pabellon = pabellon;
            this.campus = campus;
        }
    }

    public static ClassroomLocation getClassroomLocation(String courseName, String sectionCode) {
        boolean isLab = courseName.toUpperCase().contains("WEB") ||
                courseName.toUpperCase().contains("CLOUD") ||
                courseName.toUpperCase().contains("PROGRAMACI") ||
                courseName.toUpperCase().contains("SISTEMAS") ||
                courseName.toUpperCase().contains("LAB");
        return new ClassroomLocation(isLab ? "Laboratorio" : "Aula", "", "");
    }

    private String extractUserIdFromToken(String token) {
        if (token == null || !token.contains(".")) return "";
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                JsonNode payload = objectMapper.readTree(decoded);
                if (payload.hasNonNull("paoUserId")) return payload.path("paoUserId").asText();
                if (payload.hasNonNull("userId")) return payload.path("userId").asText();
                if (payload.hasNonNull("user_id")) return payload.path("user_id").asText();

                String studentCode = payload.hasNonNull("preferred_username") 
                        ? payload.path("preferred_username").asText().toLowerCase() 
                        : (payload.hasNonNull("username") ? payload.path("username").asText().toLowerCase() : "");
                if (!studentCode.isBlank() && studentPaoUserMap.containsKey(studentCode)) {
                    return studentPaoUserMap.get(studentCode);
                }
                if (payload.hasNonNull("sub")) return payload.path("sub").asText();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String extractStudentCodeFromToken(String token) {
        if (token == null || !token.contains(".")) return "";
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                JsonNode payload = objectMapper.readTree(decoded);
                if (payload.hasNonNull("preferred_username")) return payload.path("preferred_username").asText().toLowerCase();
                if (payload.hasNonNull("username")) return payload.path("username").asText().toLowerCase();
                if (payload.hasNonNull("sub")) return payload.path("sub").asText().toLowerCase();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String extractTenantIdFromToken(String token) {
        if (token != null && token.contains(".")) {
            try {
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                    JsonNode payload = objectMapper.readTree(decoded);
                    if (payload.hasNonNull("tenantId")) return payload.path("tenantId").asText();
                    if (payload.hasNonNull("tenant_id")) return payload.path("tenant_id").asText();
                    if (payload.hasNonNull("tenant")) return payload.path("tenant").asText();
                    if (payload.hasNonNull("x-tenant-id")) return payload.path("x-tenant-id").asText();
                }
            } catch (Exception ignored) {}
        }
        return "a5f469d2-3c0e-5c68-8d32-5265923a8e40";
    }

    @Override
    public StudentProfile login(String username, String password) {
        String cleanUsername = username.trim().toLowerCase();
        if (cleanUsername.matches("^\\d{8,9}$")) {
            cleanUsername = "u" + cleanUsername;
        }

        try {
            String formParams = "client_id=" + URLEncoder.encode("pao-web", StandardCharsets.UTF_8)
                    + "&grant_type=" + URLEncoder.encode("password", StandardCharsets.UTF_8)
                    + "&username=" + URLEncoder.encode(cleanUsername, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(password.trim(), StandardCharsets.UTF_8)
                    + "&scope=" + URLEncoder.encode("openid roles-client-pao-web profile email roles-realm-xpedition", StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://sso.utp.edu.pe/auth/realms/Xpedition/protocol/openid-connect/token"))
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
                this.lastActiveToken = accessToken;
                // El JWT pao-web NO incluye career/campus/cycle — enriquecer desde Portal GraphQL
                StudentProfile profile = enrichProfileFromPortal(base, accessToken);
                log.info("[UTP SSO Auth] Autenticación exitosa: {} | campus='{}' | carrera='{}' | ciclo={}",
                        profile.getStudentCode(), profile.getCampus(), profile.getCareer(), profile.getCurrentCycle());
                return profile;
            } else {
                log.warn("[UTP SSO Auth] Falló autenticación en sso.utp.edu.pe, status: {}, body: {}", response.statusCode(), response.body());
                throw new RuntimeException("Credenciales UTP inválidas o servicio temporalmente no disponible.");
            }
        } catch (Exception e) {
            log.error("[UTP SSO Auth] Error conectando con servidor SSO UTP: {}", e.getMessage());
            throw new RuntimeException("Error en autenticación UTP: " + e.getMessage());
        }
    }

    @Override
    public StudentProfile refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token inválido o vacío");
        }

        try {
            String formParams = "client_id=" + URLEncoder.encode("pao-web", StandardCharsets.UTF_8)
                    + "&grant_type=" + URLEncoder.encode("refresh_token", StandardCharsets.UTF_8)
                    + "&refresh_token=" + URLEncoder.encode(refreshToken.trim(), StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://sso.utp.edu.pe/auth/realms/Xpedition/protocol/openid-connect/token"))
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
                log.info("[UTP SSO Refresh] Token renovado exitosamente para alumno: {}", profile.getStudentCode());
                return profile;
            } else {
                log.warn("[UTP SSO Refresh] Falló renovación en sso.utp.edu.pe, status: {}, body: {}", response.statusCode(), response.body());
                throw new RuntimeException("Refresh token expirado o inválido en Keycloak UTP.");
            }
        } catch (Exception e) {
            log.error("[UTP SSO Refresh] Error conectando con servidor SSO UTP: {}", e.getMessage());
            throw new RuntimeException("Error renovando sesión UTP: " + e.getMessage());
        }
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
            studentUserId = !studentCode.isBlank() ? studentCode : "usr-student";
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
        String studentCode = base.getStudentCode() != null ? base.getStudentCode().toLowerCase() : "";

        String portalUrl = "https://api-portal.utpxpedition.com/graphql";
        Map<String, String> commonHeaders = Map.of(
                "applicationid", "APP00002",
                "authorization", "Bearer " + accessToken,
                "content-type", "application/json",
                "isreservation", "false",
                "user-id", studentCode,
                "user-role", "student"
        );

        // 1. getProfile → academic.progDesc (carrera legible) + campusDesc
        try {
            String profileQuery = "{\"operationName\":\"getProfile\",\"variables\":{}," +
                    "\"query\":\"query getProfile { student { academic { campusDesc progDesc } } }\"}";

            HttpRequest.Builder profileReqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(portalUrl))
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
        } catch (Exception e) {
            log.warn("[UTP Portal Enrich] No se pudo obtener getProfile: {}", e.getMessage());
        }

        // 2. GetCourseSummary → summary.relativeCycle (ciclo real) + summary.campus (fallback)
        try {
            String summaryQuery = "{\"operationName\":\"GetCourseSummary\",\"variables\":{\"periodId\":\"2263\"}," +
                    "\"query\":\"query GetCourseSummary($periodId: String!) { getCourseSummary(periodId: $periodId) { summary { campus relativeCycle } } }\"}";

            HttpRequest.Builder summaryReqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(portalUrl))
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
        if (token != null && token.contains(".")) {
            // 1. Intentar extracción 100% real de aulas, pabellones y docentes via Portal GraphQL
            try {
                ScheduleInterval graphqlInterval = fetchScheduleFromPortalGraphQL(token, period);
                if (graphqlInterval != null && !graphqlInterval.getClasses().isEmpty()) {
                    log.info("[UTP Portal GraphQL] Horario extraído exitosamente con {} clases reales", graphqlInterval.getClasses().size());
                    return graphqlInterval;
                }
            } catch (Exception e) {
                log.warn("[UTP Portal GraphQL] Error consultando GraphQL portal: {}. Intentando fallback PAO...", e.getMessage());
            }

            // 2. Fallback a PAO Calendar si GraphQL falla
            try {
                String userId = extractUserIdFromToken(token);
                String tenantId = extractTenantIdFromToken(token);

                if (!userId.isBlank()) {
                    String dateToQuery = LocalDate.now().toString() + " 00:00:00";
                    String url = "https://api-pao.utpxpedition.com/course/student/calendar?userId="
                            + URLEncoder.encode(userId, StandardCharsets.UTF_8)
                            + "&dateToQuery=" + URLEncoder.encode(dateToQuery, StandardCharsets.UTF_8)
                            + "&intervalMode=period";

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(15))
                            .header("Authorization", "Bearer " + token)
                            .header("x-tenant-id", tenantId)
                            .header("user-id", userId)
                            .header("user-role", "STUDENT")
                            .header("origin", "https://class.utp.edu.pe")
                            .header("referer", "https://class.utp.edu.pe/")
                            .header("Accept", "application/json, text/plain, */*")
                            .GET()
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        JsonNode root = objectMapper.readTree(response.body());
                        JsonNode intervalNode = root.path("data").path("current_interval");
                        if (intervalNode != null && intervalNode.hasNonNull("events")) {
                            return parsePaoCalendarResponse(intervalNode);
                        }
                    } else {
                        log.warn("[UTP PAO Calendar] Status no exitoso ({}) desde api-pao.utpxpedition.com", response.statusCode());
                    }
                }
            } catch (Exception e) {
                log.warn("[UTP PAO Calendar] Excepción al consultar horario en vivo UTP: {}", e.getMessage());
            }
        }

        // Sin sesión activa o fallo en UTP: estructura vacía limpia
        return ScheduleInterval.builder()
                .id("interval-empty")
                .periodName(period != null ? period : "2026 - Ciclo 2 Agosto")
                .weekNumber(1)
                .totalWeeks(18)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusWeeks(18))
                .courses(List.of())
                .classes(List.of())
                .build();
    }

    private ScheduleInterval fetchScheduleFromPortalGraphQL(String token, String period) {
        String studentCode = extractStudentCodeFromToken(token);

        // Sincronizar catálogo de pabellones/edificios si no existe en memoria
        ensureBuildingsLoaded(token, studentCode);

        // Identificar período de consulta (ej. "2263")
        String periodCode = (period != null && period.matches("^\\d{4}$")) ? period : "2263";

        // Consultar semanas del ciclo (Semana 1 = 10 de Agosto 2026)
        long semesterStartMillis = 1786338000000L; // 2026-08-10 00:00:00 UTC (inicio oficial de periodo)
        long nowMillis = System.currentTimeMillis();
        long diffMillis = nowMillis - semesterStartMillis;
        int currentWeekCalc = (int) Math.max(1, Math.min(18, (diffMillis / (7L * 24 * 60 * 60 * 1000L)) + 1));

        List<ClassSession> allClasses = new ArrayList<>();
        Map<String, Course> coursesMap = new HashMap<>();

        String scheduleGraphqlQuery = "query getSchedules($date: Float!, $periods: [String!]) {\n" +
                "  scheduleByDate(filters: {date: $date, classTypes: [1, 2, 3, 4, 5, 6], periods: $periods}) {\n" +
                "    dates {\n" +
                "      date\n" +
                "      items {\n" +
                "        name\n" +
                "        period\n" +
                "        class {\n" +
                "          id\n" +
                "          start\n" +
                "          end\n" +
                "          descAmb\n" +
                "          professors { firstName lastName }\n" +
                "          location {\n" +
                "            classRoom { desc id floor }\n" +
                "            building { id desc address }\n" +
                "          }\n" +
                "          instructionMode\n" +
                "          linkZoom\n" +
                "          linkCourseClass\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n";

        for (int w = 0; w < 18; w++) {
            long weekTimestamp = semesterStartMillis + (w * 7L * 24 * 60 * 60 * 1000L);
            try {
                Map<String, Object> vars = Map.of(
                        "date", weekTimestamp,
                        "periods", List.of(periodCode)
                );
                Map<String, Object> bodyObj = Map.of(
                        "operationName", "getSchedules",
                        "variables", vars,
                        "query", scheduleGraphqlQuery
                );

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://api-portal.utpxpedition.com/graphql"))
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
                    JsonNode root = objectMapper.readTree(resp.body());
                    JsonNode datesNode = root.path("data").path("scheduleByDate").path("dates");
                    if (datesNode.isArray()) {
                        for (JsonNode dNode : datesNode) {
                            JsonNode itemsNode = dNode.path("items");
                            if (itemsNode.isArray()) {
                                for (JsonNode it : itemsNode) {
                                    JsonNode cls = it.path("class");
                                    if (cls.isMissingNode() || cls.isNull()) continue;

                                    String courseNameRaw = it.path("name").asText();
                                    ParsedTitle pt = parseTitle(courseNameRaw);
                                    String cleanCourseName = pt.cleanTitle;

                                    String classId = cls.path("id").asText();
                                    String linkCourseClass = cls.hasNonNull("linkCourseClass") ? cls.path("linkCourseClass").asText() : null;

                                    String sectionCode = !pt.sectionCode.isBlank() ? pt.sectionCode : (it.hasNonNull("section") ? it.path("section").asText() : "");
                                    if (sectionCode.isBlank() && classId.matches("^\\d+$")) {
                                        sectionCode = classId;
                                    }

                                    String courseUuid = "";
                                    String sectionUuid = "";
                                    if (linkCourseClass != null) {
                                        Matcher mUuids = Pattern.compile("courses?/([0-9a-fA-F-]+)/sections?/([0-9a-fA-F-]+)").matcher(linkCourseClass);
                                        if (mUuids.find()) {
                                            courseUuid = mUuids.group(1);
                                            sectionUuid = mUuids.group(2);
                                        } else {
                                            Matcher mLink = Pattern.compile("courses?/(\\d+)").matcher(linkCourseClass);
                                            if (mLink.find() && sectionCode.isBlank()) {
                                                sectionCode = mLink.group(1);
                                            }
                                        }
                                    }

                                    registerCourseRef(studentCode, cleanCourseName, sectionCode, courseUuid, sectionUuid, null);
                                    registerCourseRef(studentCode, courseNameRaw, sectionCode, courseUuid, sectionUuid, null);

                                    long startEpoch = cls.path("start").asLong();
                                    long endEpoch = cls.path("end").asLong();
                                    String descAmb = cls.path("descAmb").asText();
                                    String instructionMode = cls.path("instructionMode").asText("P");
                                    String linkZoom = cls.hasNonNull("linkZoom") ? cls.path("linkZoom").asText() : null;

                                    JsonNode prof = cls.path("professors");
                                    String teacher = "";
                                    if (prof.isArray() && !prof.isEmpty()) {
                                        JsonNode p0 = prof.get(0);
                                        teacher = formatName(p0.path("firstName").asText() + " " + p0.path("lastName").asText());
                                    } else if (prof.hasNonNull("firstName")) {
                                        teacher = formatName(prof.path("firstName").asText() + " " + prof.path("lastName").asText());
                                    }

                                    JsonNode loc = cls.path("location");
                                    JsonNode classRoom = loc.path("classRoom");
                                    JsonNode building = loc.path("building");

                                    String roomId = classRoom.path("id").asText();
                                    String floor = classRoom.path("floor").asText();
                                    String bldgId = building.path("id").asText();
                                    String bldgDesc = buildingCache.getOrDefault(bldgId, bldgId);
                                    if (bldgDesc == null || bldgDesc.isBlank()) {
                                        bldgDesc = building.path("desc").asText(bldgId);
                                    }
                                    if (bldgDesc == null || bldgDesc.isBlank()) {
                                        bldgDesc = !bldgId.isBlank() ? bldgId : "Pabellón UTP";
                                    }

                                    LocalDateTime startAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(startEpoch), ZoneId.of("America/Lima"));
                                    LocalDateTime finishAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(endEpoch), ZoneId.of("America/Lima"));


                                    ClassSession session = ClassSession.builder()
                                            .id(classId.isBlank() ? "gql-" + startEpoch : classId)
                                            .courseCode(cleanCourseName)
                                            .courseName(cleanCourseName)
                                            .section(sectionCode)
                                            .classroom(roomId.isBlank() ? "Aula General" : roomId)
                                            .building(bldgDesc)
                                            .floor(floor)
                                            .environmentType(descAmb)
                                            .teacher(teacher)
                                            .modality(instructionMode)
                                            .startAt(startAt)
                                            .finishAt(finishAt)
                                            .zoomLink(linkZoom)
                                            .classLink(linkCourseClass)
                                            .build();

                                    allClasses.add(session);

                                    if (!coursesMap.containsKey(cleanCourseName)) {
                                        coursesMap.put(cleanCourseName, Course.builder()
                                                .id("c-" + cleanCourseName.toLowerCase().replaceAll("[^a-z0-9]", "-"))
                                                .code("100000" + cleanCourseName.substring(0, Math.min(4, cleanCourseName.length())))
                                                .name(cleanCourseName)
                                                .section(sectionCode)
                                                .credits(3)
                                                .teacher(teacher)
                                                .modality(instructionMode.equals("P") ? "Presencial" : (instructionMode.equals("R") ? "Remoto Zoom" : "Virtual"))
                                                .weeklyHours(4)
                                                .build());
                                    } else {
                                        Course existing = coursesMap.get(cleanCourseName);
                                        boolean needsTeacher = (existing.getTeacher() == null || existing.getTeacher().isBlank()) && !teacher.isBlank();
                                        boolean needsSection = (existing.getSection() == null || existing.getSection().isBlank()) && !sectionCode.isBlank();
                                        if (needsTeacher || needsSection) {
                                            coursesMap.put(cleanCourseName, Course.builder()
                                                    .id(existing.getId())
                                                    .code(existing.getCode())
                                                    .name(existing.getName())
                                                    .section(needsSection ? sectionCode : existing.getSection())
                                                    .credits(existing.getCredits())
                                                    .teacher(needsTeacher ? teacher : existing.getTeacher())
                                                    .modality(existing.getModality())
                                                    .weeklyHours(existing.getWeeklyHours())
                                                    .build());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[UTP Portal GraphQL] Falló consulta de semana {}: {}", w + 1, e.getMessage());
            }
        }

        if (allClasses.isEmpty()) {
            return null;
        }

        return ScheduleInterval.builder()
                .id("interval-graphql-" + currentWeekCalc)
                .periodName("2026 - Ciclo 2 Agosto")
                .weekNumber(currentWeekCalc)
                .totalWeeks(18)
                .startDate(LocalDate.of(2026, 8, 10))
                .endDate(LocalDate.of(2026, 8, 10).plusWeeks(18))
                .courses(new ArrayList<>(coursesMap.values()))
                .classes(allClasses)
                .build();
    }

    private void ensureBuildingsLoaded(String token, String studentCode) {
        if (!buildingCache.isEmpty()) {
            return;
        }

        try {
            Map<String, Object> bodyObj = Map.of(
                    "operationName", "getBuildings",
                    "variables", Map.of(),
                    "query", "query getBuildings {\n  buildings {\n    id\n    description\n    address\n  }\n}\n"
            );

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api-portal.utpxpedition.com/graphql"))
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
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode bArray = root.path("data").path("buildings");
                if (bArray.isArray()) {
                    for (JsonNode b : bArray) {
                        String id = b.path("id").asText();
                        String desc = b.path("description").asText();
                        if (desc.isBlank()) desc = b.path("address").asText();
                        if (!id.isBlank() && !desc.isBlank()) {
                            buildingCache.put(id, desc);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[UTP Portal GraphQL] No se pudo cargar catálogo de pabellones: {}", e.getMessage());
        }
    }

    public static class CourseRef {
        public final String courseName;
        public final String numericSection;
        public final String courseUuid;
        public final String sectionUuid;
        public final String syllabusUrl;

        public CourseRef(String courseName, String numericSection, String courseUuid, String sectionUuid, String syllabusUrl) {
            this.courseName = courseName;
            this.numericSection = numericSection != null ? numericSection : "";
            this.courseUuid = courseUuid != null ? courseUuid : "";
            this.sectionUuid = sectionUuid != null ? sectionUuid : "";
            this.syllabusUrl = syllabusUrl != null ? syllabusUrl : "";
        }
    }

    private final Map<String, Map<String, CourseRef>> userCourseRefMap = new ConcurrentHashMap<>();

    private void registerCourseRef(String studentCode, String name, String numericSection, String courseUuid, String sectionUuid, String syllabusUrl) {
        if (name == null || name.isBlank()) return;
        String userKey = (studentCode != null && !studentCode.isBlank()) ? studentCode.toLowerCase() : "shared";
        Map<String, CourseRef> courseRefMap = userCourseRefMap.computeIfAbsent(userKey, k -> new ConcurrentHashMap<>());

        CourseRef ref = new CourseRef(name, numericSection, courseUuid, sectionUuid, syllabusUrl);
        String raw = name.trim().toUpperCase();
        String norm = normalizeSearchKey(raw);
        courseRefMap.put(raw, ref);
        courseRefMap.put(norm, ref);
        if (numericSection != null && !numericSection.isBlank() && numericSection.matches("^\\d+$")) {
            courseRefMap.put(numericSection, ref);
        }
        if (sectionUuid != null && !sectionUuid.isBlank()) {
            courseRefMap.put(sectionUuid.toLowerCase(), ref);
        }
        if (courseUuid != null && !courseUuid.isBlank()) {
            courseRefMap.put(courseUuid.toLowerCase(), ref);
        }
    }

    private CourseRef findCourseRef(String studentCode, String courseCodeOrName) {
        if (courseCodeOrName == null || courseCodeOrName.isBlank()) return null;
        String userKey = (studentCode != null && !studentCode.isBlank()) ? studentCode.toLowerCase() : "shared";
        Map<String, CourseRef> courseRefMap = userCourseRefMap.get(userKey);
        if (courseRefMap == null || courseRefMap.isEmpty()) return null;

        String raw = courseCodeOrName.trim().toUpperCase();
        CourseRef ref = courseRefMap.get(raw);
        if (ref != null) return ref;

        String norm = normalizeSearchKey(raw);
        ref = courseRefMap.get(norm);
        if (ref != null) return ref;

        for (Map.Entry<String, CourseRef> entry : courseRefMap.entrySet()) {
            String kNorm = normalizeSearchKey(entry.getKey());
            if (kNorm.contains(norm) || norm.contains(kNorm)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void populateCourseSectionsFromPao(String token) {
        if (token == null || token.isBlank()) return;
        try {
            String userId = extractUserIdFromToken(token);
            String tenantId = extractTenantIdFromToken(token);

            if (userId.isBlank()) {
                return;
            }

            // 1. Sincronizar secciones desde calendario PAO
            String dateToQuery = LocalDate.now().toString() + " 00:00:00";
            String url = "https://api-pao.utpxpedition.com/course/student/calendar?userId="
                    + URLEncoder.encode(userId, StandardCharsets.UTF_8)
                    + "&dateToQuery=" + URLEncoder.encode(dateToQuery, StandardCharsets.UTF_8)
                    + "&intervalMode=period";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", token.startsWith("Bearer ") ? token : "Bearer " + token)
                    .header("x-tenant-id", tenantId)
                    .header("user-id", userId)
                    .header("user-id-to-access", "")
                    .header("user-role", "STUDENT")
                    .header("user-role-to-access", "")
                    .header("transaction-id", java.util.UUID.randomUUID().toString())
                    .header("origin", "https://class.utp.edu.pe")
                    .header("referer", "https://class.utp.edu.pe/")
                    .header("Accept", "application/json, text/plain, */*")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode events = root.path("data").path("current_interval").path("events");
                if (events.isArray()) {
                    for (JsonNode ev : events) {
                        String rawTitle = ev.path("title").asText();
                        ParsedTitle pt = parseTitle(rawTitle);
                        String courseId = ev.path("metadata").path("courseId").asText();
                        String secMeta = ev.path("metadata").path("sectionId").asText();
                        String syllabusUrl = ev.path("metadata").path("syllabusUrl").asText("");
                        
                        String numericSection = pt.sectionCode;
                        String courseUuid = courseId.matches("^[0-9a-fA-F-]{36}$") ? courseId : "";
                        String sectionUuid = secMeta.matches("^[0-9a-fA-F-]{36}$") ? secMeta : "";
                        if (numericSection.isBlank() && secMeta.matches("^\\d+$")) {
                            numericSection = secMeta;
                        }

                        String studentCode = extractStudentCodeFromToken(token);
                        registerCourseRef(studentCode, pt.cleanTitle, numericSection, courseUuid, sectionUuid, syllabusUrl);
                        registerCourseRef(studentCode, rawTitle, numericSection, courseUuid, sectionUuid, syllabusUrl);
                        if (!courseId.isBlank()) registerCourseRef(studentCode, courseId, numericSection, courseUuid, sectionUuid, syllabusUrl);
                    }
                }
            }

            log.info("[UTP PAO Gateway] Mapeadas referencias de asignaturas en memoria para alumno");
        } catch (Exception e) {
            log.warn("[UTP PAO Gateway] No se pudieron sincronizar secciones desde PAO: {}", e.getMessage());
        }
    }

    private String normalizeSearchKey(String str) {
        if (str == null) return "";
        return java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-zA-Z0-9]", "")
                .toUpperCase();
    }

    private ScheduleInterval parsePaoCalendarResponse(JsonNode intervalNode) {
        String periodName = intervalNode.path("period_name").asText("2026 - Ciclo 2 Agosto");
        int weekNumber = intervalNode.path("week_number").asInt(1);
        int totalWeeks = intervalNode.path("total_weeks").asInt(18);

        List<ClassSession> classes = new ArrayList<>();
        List<Course> courses = new ArrayList<>();
        List<String> seenCourseIds = new ArrayList<>();

        JsonNode events = intervalNode.path("events");
        if (events.isArray()) {
            for (JsonNode ev : events) {
                boolean isLongLasting = ev.path("isLongLasting").asBoolean(false);
                if (isLongLasting) {
                    continue;
                }

                String id = ev.path("id").asText();
                String rawTitle = ev.path("title").asText();
                String modality = ev.path("modality").asText("P");
                String startAtStr = ev.path("startAt").asText();
                String finishAtStr = ev.path("finishAt").asText();
                String zoomLink = ev.path("metadata").path("zoomLink").asText(null);
                String courseId = ev.path("metadata").path("courseId").asText("");
                String sectionId = ev.path("metadata").path("sectionId").asText("");
                String syllabusUrl = ev.path("metadata").path("syllabusUrl").asText("");

                ParsedTitle parsed = parseTitle(rawTitle);
                ClassroomLocation loc = getClassroomLocation(parsed.cleanTitle, parsed.sectionCode);

                String courseUuid = courseId.matches("^[0-9a-fA-F-]{36}$") ? courseId : "";
                String sectionUuid = sectionId.matches("^[0-9a-fA-F-]{36}$") ? sectionId : "";
                String finalSection = !parsed.sectionCode.isBlank() ? parsed.sectionCode : (sectionId.matches("^\\d+$") ? sectionId : "");

                registerCourseRef(null, parsed.cleanTitle, finalSection, courseUuid, sectionUuid, syllabusUrl);
                registerCourseRef(null, rawTitle, finalSection, courseUuid, sectionUuid, syllabusUrl);
                if (!courseId.isBlank()) registerCourseRef(null, courseId, finalSection, courseUuid, sectionUuid, syllabusUrl);

                LocalDateTime startAt = LocalDateTime.parse(startAtStr.replace(" ", "T"));
                LocalDateTime finishAt = LocalDateTime.parse(finishAtStr.replace(" ", "T"));

                classes.add(ClassSession.builder()
                        .id(id)
                        .courseCode(courseId.isBlank() ? parsed.cleanTitle : courseId)
                        .courseName(parsed.cleanTitle)
                        .section(parsed.sectionCode.isBlank() ? sectionId : parsed.sectionCode)
                        .classroom(loc.aula)
                        .building(loc.pabellon)
                        .floor(String.valueOf((Math.abs(parsed.cleanTitle.hashCode()) % 6) + 2))
                        .environmentType(modality.equals("P") ? "AULA TEÓRICA" : "AULA VIRTUAL")
                        .teacher("")
                        .modality(modality)
                        .startAt(startAt)
                        .finishAt(finishAt)
                        .zoomLink(zoomLink)
                        .build());

                if (!seenCourseIds.contains(parsed.cleanTitle)) {
                    seenCourseIds.add(parsed.cleanTitle);
                    courses.add(Course.builder()
                            .id("c-" + parsed.cleanTitle.toLowerCase().replaceAll("[^a-z0-9]", "-"))
                            .code(courseId.isBlank() ? "100000" + parsed.cleanTitle.substring(0, Math.min(4, parsed.cleanTitle.length())) : courseId)
                            .name(parsed.cleanTitle)
                            .section(parsed.sectionCode.isBlank() ? sectionId : parsed.sectionCode)
                            .credits(3)
                            .modality(modality.equals("P") ? "Presencial" : (modality.equals("R") ? "Remoto Zoom" : "Virtual"))
                            .weeklyHours(4)
                            .build());
                }
            }
        }

        return ScheduleInterval.builder()
                .id("interval-live-" + weekNumber)
                .periodName(periodName)
                .weekNumber(weekNumber)
                .totalWeeks(totalWeeks)
                .startDate(LocalDate.now().minusWeeks(weekNumber - 1))
                .endDate(LocalDate.now().plusWeeks(totalWeeks - weekNumber))
                .courses(courses)
                .classes(classes)
                .build();
    }

    private static class ParsedTitle {
        String cleanTitle;
        String sectionCode = "";
    }

    private ParsedTitle parseTitle(String raw) {
        ParsedTitle pt = new ParsedTitle();
        String title = raw.trim();

        // Extraer sección (e.g. (45104))
        Pattern pSec = Pattern.compile("\\(\\s*(\\d+)\\s*\\)");
        Matcher mSec = pSec.matcher(title);
        if (mSec.find()) {
            pt.sectionCode = mSec.group(1);
            title = title.replace(mSec.group(0), "").trim();
        }

        // Limpiar (Semana X) y sufijos de día (únicamente nombres de día reales)
        title = title.replaceAll("(?i)\\(\\s*semana\\s*\\d+\\s*\\)", "").trim();
        title = title.replaceAll("(?i)-\\s*(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo|sesi[oó]n)$", "").trim();

        // Formato capitalizado de nombre de curso
        pt.cleanTitle = formatCourseName(title);
        return pt;
    }

    private String formatCourseName(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String[] words = raw.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i].toLowerCase();
            if (w.equalsIgnoreCase("ti") || w.equalsIgnoreCase("ia") || w.equalsIgnoreCase("aws") || w.equalsIgnoreCase("utp")) {
                sb.append(w.toUpperCase());
            } else if (i > 0 && (w.equals("de") || w.equals("del") || w.equals("la") || w.equals("para") || w.equals("por") || w.equals("en") || w.equals("y") || w.equals("e"))) {
                sb.append(w);
            } else if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
            if (i < words.length - 1) sb.append(" ");
        }
        return sb.toString().replace("- ", "- ").trim();
    }

    private String formatName(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String[] parts = raw.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].toLowerCase();
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
            if (i < parts.length - 1) sb.append(" ");
        }
        return sb.toString();
    }

    @Override
    public String fetchSyllabusPdfText(String token, String courseCodeOrUrl) {
        if (courseCodeOrUrl == null || courseCodeOrUrl.isBlank()) {
            return "";
        }

        String effectiveToken = (token != null && !token.isBlank()) ? token : this.lastActiveToken;

        try {
            String pdfUrl = courseCodeOrUrl.trim();

            if (!pdfUrl.startsWith("http://") && !pdfUrl.startsWith("https://")) {
                String userId = extractUserIdFromToken(effectiveToken);
                String tenantId = extractTenantIdFromToken(effectiveToken);

                String studentCode = extractStudentCodeFromToken(effectiveToken);
                CourseRef ref = findCourseRef(studentCode, pdfUrl);
                if (ref == null || (ref.syllabusUrl.isBlank() && ref.numericSection.isBlank() && ref.courseUuid.isBlank())) {
                    if (effectiveToken != null && !effectiveToken.isBlank()) {
                        populateCourseSectionsFromPao(effectiveToken);
                        ref = findCourseRef(studentCode, pdfUrl);
                    }
                }

                if (ref != null && !ref.syllabusUrl.isBlank()) {
                    pdfUrl = ref.syllabusUrl;
                    log.info("[UtpPortalGatewayAdapter] URL directa de sílabo obtenida de CourseRef: {}", pdfUrl);
                } else {
                    String syllabusApiUrl = null;
                    if (ref != null && !ref.courseUuid.isBlank() && !ref.sectionUuid.isBlank()) {
                        syllabusApiUrl = "https://api-pao.utpxpedition.com/course/student/courses/" 
                                + ref.courseUuid + "/sections/" + ref.sectionUuid + "/syllabus";
                    } else {
                        String targetSec = (ref != null && !ref.numericSection.isBlank()) ? ref.numericSection : pdfUrl;
                        if (targetSec.matches("^\\d+$")) {
                            syllabusApiUrl = "https://api-pao.utpxpedition.com/course/student/sections/" 
                                    + URLEncoder.encode(targetSec, StandardCharsets.UTF_8) + "/syllabus";
                        }
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
                        if (res.statusCode() == 200) {
                            JsonNode node = objectMapper.readTree(res.body());
                            if (node.has("data") && node.path("data").hasNonNull("syllabusUrl")) {
                                pdfUrl = node.path("data").path("syllabusUrl").asText();
                                log.info("[UtpPortalGatewayAdapter] URL de sílabo obtenida desde PAO API: {}", pdfUrl);
                            }
                        } else {
                            log.warn("[UtpPortalGatewayAdapter] Respuesta no exitosa ({}) de PAO syllabus para {}: {}", res.statusCode(), syllabusApiUrl, res.body());
                        }
                    }
                }
            }

            if (pdfUrl.startsWith("http://") || pdfUrl.startsWith("https://")) {
                String safeUri = pdfUrl.replace(" ", "%20");
                log.info("[UtpPortalGatewayAdapter] Descargando binario PDF desde: {}", safeUri);
                HttpRequest pdfReq = HttpRequest.newBuilder()
                        .uri(URI.create(safeUri))
                        .timeout(Duration.ofSeconds(15))
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
                } else {
                    log.warn("[UtpPortalGatewayAdapter] Error descargando PDF de S3, status: {}", pdfRes.statusCode());
                }
            }
        } catch (Exception e) {
            log.warn("[UtpPortalGatewayAdapter] Error descargando/extrayendo PDF de sílabo para '{}': {}", courseCodeOrUrl, e.getMessage());
        }

        return "";
    }

    private final Map<String, String> sectionToCourseCodeCache = new ConcurrentHashMap<>();
    private final Map<String, String> courseNameToCourseCodeCache = new ConcurrentHashMap<>();

    public String resolveCourseCode(String sectionId, String courseName, String token) {
        if (sectionId != null && !sectionId.isBlank() && sectionToCourseCodeCache.containsKey(sectionId)) {
            return sectionToCourseCodeCache.get(sectionId);
        }
        if (courseName != null && !courseName.isBlank()) {
            String norm = normalizeCourseName(courseName);
            if (courseNameToCourseCodeCache.containsKey(norm)) {
                return courseNameToCourseCodeCache.get(norm);
            }
        }

        // Si no está en caché y tenemos token, consultar dashboard-courses de Class
        if (token != null && !token.isBlank()) {
            fetchAndCacheDashboardCourses(token);
            if (sectionId != null && !sectionId.isBlank() && sectionToCourseCodeCache.containsKey(sectionId)) {
                return sectionToCourseCodeCache.get(sectionId);
            }
            if (courseName != null && !courseName.isBlank()) {
                String norm = normalizeCourseName(courseName);
                if (courseNameToCourseCodeCache.containsKey(norm)) {
                    return courseNameToCourseCodeCache.get(norm);
                }
            }
        }

        return null;
    }

    private void fetchAndCacheDashboardCourses(String token) {
        String effectiveToken = (token != null && token.startsWith("Bearer ")) ? token.substring(7).trim() : token;
        String userId = extractUserIdFromToken(effectiveToken);
        if (userId == null || userId.isBlank()) return;

        String url = "https://api-pao.utpxpedition.com/learning/student/" + userId + "/dashboard-courses";
        String tenantId = extractTenantIdFromToken(effectiveToken);

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
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                JsonNode dataNode = root.path("data");
                if (dataNode.isArray()) {
                    for (JsonNode cNode : dataNode) {
                        String sId = cNode.path("sectionId").asText(null);
                        String cCode = cNode.path("courseCode").asText(null);
                        String sCode = cNode.path("sectionCode").asText("");
                        String cName = cNode.path("name").asText(null);

                        if ((cCode == null || cCode.isBlank()) && !sCode.isBlank()) {
                            Matcher m = Pattern.compile("(?i)(100000[A-Z0-9]{4})").matcher(sCode);
                            if (m.find()) {
                                cCode = m.group(1);
                            }
                        }

                        if (cCode != null && !cCode.isBlank()) {
                            if (sId != null && !sId.isBlank()) {
                                sectionToCourseCodeCache.put(sId, cCode);
                            }
                            if (cName != null && !cName.isBlank()) {
                                courseNameToCourseCodeCache.put(normalizeCourseName(cName), cCode);
                            }
                        }
                    }
                    log.info("[UtpPortalGatewayAdapter] Dashboard courses sincronizados en caché. Total secciones: {}", sectionToCourseCodeCache.size());
                }
            }
        } catch (Exception e) {
            log.warn("[UtpPortalGatewayAdapter] No se pudo cargar dashboard-courses: {}", e.getMessage());
        }
    }

    private String normalizeCourseName(String name) {
        if (name == null) return "";
        return name.toUpperCase()
                .replaceAll("[ÁÀÄÂ]", "A")
                .replaceAll("[ÉÈËÊ]", "E")
                .replaceAll("[ÍÌÏÎ]", "I")
                .replaceAll("[ÓÒÖÔ]", "O")
                .replaceAll("[ÚÙÜÛ]", "U")
                .replaceAll("[^A-Z0-9]", "")
                .trim();
    }

    @Override
    public TaskSpecification fetchTaskSpecification(String sectionId, String activityId, String token) {
        String effectiveToken = (token != null && token.startsWith("Bearer ")) ? token.substring(7).trim() : token;
        String userId = extractUserIdFromToken(effectiveToken);
        String tenantId = extractTenantIdFromToken(effectiveToken);

        String url = "https://api-pao.utpxpedition.com/course/student/sections/" + sectionId + "/homeworks/" + activityId + "/resume";
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
                JsonNode data = root.path("data");

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

                return TaskSpecification.builder()
                        .id(data.path("id").asText(activityId))
                        .title(data.path("title").asText("Asignación"))
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
                        .build();
            } else {
                log.warn("[UtpPortalGatewayAdapter] Error {} al obtener tarea {}: {}", resp.statusCode(), activityId, resp.body());
            }
        } catch (Exception e) {
            log.error("[UtpPortalGatewayAdapter] Excepción al consultar homework resume {}: {}", activityId, e.getMessage());
        }

        return TaskSpecification.builder()
                .id(activityId)
                .title("Asignación no disponible")
                .courseCode(resolvedCourseCode)
                .sectionId(sectionId)
                .descriptionMarkdown("")
                .gradingRubric(new ArrayList<>())
                .submissionTypes(List.of("online_upload"))
                .build();
    }

    @Override
    public List<AcademicActivity> fetchCalendarActivities(String dateToQuery, String intervalMode, String token) {
        String effectiveToken = (token != null && token.startsWith("Bearer ")) ? token.substring(7).trim() : token;
        String userId = extractUserIdFromToken(effectiveToken);
        String tenantId = extractTenantIdFromToken(effectiveToken);

        String effectiveDate = (dateToQuery != null && !dateToQuery.isBlank()) ? dateToQuery : "2026-09-21+00:00:00";
        String effectiveMode = (intervalMode != null && !intervalMode.isBlank()) ? intervalMode : "period";

        String url = "https://api-pao.utpxpedition.com/course/student/calendar/activities?userId=" + userId
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
                        String cCode = resolveCourseCode(secId, cName, effectiveToken);

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
                }
            } else {
                log.warn("[UtpPortalGatewayAdapter] Error {} consultando calendar activities: {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.error("[UtpPortalGatewayAdapter] Excepción en fetchCalendarActivities: {}", e.getMessage());
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
        String studentCode = extractStudentCodeFromToken(effectiveToken);
        if (studentCode == null || studentCode.isBlank()) {
            throw new IllegalArgumentException("Token de autenticación no contiene código de estudiante válido");
        }
        String effectivePeriod = (periodId != null && !periodId.isBlank()) ? periodId : "2263";

        String url = "https://api-portal.utpxpedition.com/graphql";
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
                JsonNode summaryNode = root.path("data").path("getCourseSummary").path("summary");
                if (!summaryNode.isMissingNode() && !summaryNode.isNull()) {
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
                }

                JsonNode coursesNode = root.path("data").path("getCourseSummary").path("courses");
                if (coursesNode.isArray()) {
                    for (JsonNode cNode : coursesNode) {
                        List<CourseSummaryData.EvaluationGrade> evGrades = new ArrayList<>();
                        if (cNode.has("evaluations") && cNode.path("evaluations").isArray()) {
                            for (JsonNode ev : cNode.path("evaluations")) {
                                String val = ev.path("value").asText("");
                                boolean isGraded = false;
                                try {
                                    isGraded = Double.parseDouble(val) > 1.0;
                                } catch (Exception ignored) {}

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
                                .evaluations(evGrades)
                                .build());
                    }
                }
            } else {
                log.warn("[UtpPortalGatewayAdapter] Error {} en GetCourseSummary: {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.error("[UtpPortalGatewayAdapter] Excepción en fetchCourseSummary: {}", e.getMessage());
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

