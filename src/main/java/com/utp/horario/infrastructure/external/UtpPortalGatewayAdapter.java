package com.utp.horario.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.model.ClassSession;
import com.utp.horario.domain.model.Course;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.model.StudentProfile;
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
    private final Map<String, String> courseToSectionMap = new ConcurrentHashMap<>();

    public UtpPortalGatewayAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
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
        if (token == null || !token.contains(".")) return "4e535263-79a0-5890-ae33-72a7aa0629ab";
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                JsonNode payload = objectMapper.readTree(decoded);
                if (payload.hasNonNull("paoUserId")) return payload.path("paoUserId").asText();
                if (payload.hasNonNull("userId")) return payload.path("userId").asText();
                if (payload.hasNonNull("user_id")) return payload.path("user_id").asText();
            }
        } catch (Exception ignored) {}
        return "4e535263-79a0-5890-ae33-72a7aa0629ab";
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
        if (token == null || !token.contains(".")) return "a5f469d2-3c0e-5c68-8d32-5265923a8e40";
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

                String studentName = cleanUsername.toUpperCase();
                String studentCode = cleanUsername.toUpperCase();
                String studentEmail = cleanUsername + "@utp.edu.pe";
                String studentUserId = "";

                if (accessToken.contains(".")) {
                    String[] parts = accessToken.split("\\.");
                    if (parts.length >= 2) {
                        byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                        JsonNode payload = objectMapper.readTree(decoded);
                        if (payload.hasNonNull("name")) studentName = payload.path("name").asText();
                        if (payload.hasNonNull("email")) studentEmail = payload.path("email").asText();
                        if (payload.hasNonNull("preferred_username")) studentCode = payload.path("preferred_username").asText().toUpperCase();
                        if (payload.hasNonNull("userId")) {
                            studentUserId = payload.path("userId").asText();
                        } else if (payload.hasNonNull("user_id")) {
                            studentUserId = payload.path("user_id").asText();
                        } else if (payload.hasNonNull("paoUserId")) {
                            studentUserId = payload.path("paoUserId").asText();
                        } else if (payload.hasNonNull("sub")) {
                            studentUserId = payload.path("sub").asText();
                        }
                    }
                }

                String studentCareer = "";
                String studentCampus = "";
                int studentCycle = 1;
                if (accessToken.contains(".")) {
                    try {
                        String[] parts = accessToken.split("\\.");
                        if (parts.length >= 2) {
                            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
                            JsonNode payload = objectMapper.readTree(decoded);
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
                    studentUserId = studentCode;
                }

                log.info("[UTP SSO Auth] Autenticación exitosa en vivo para alumno: {} ({}) - Campus: '{}'", studentCode, studentName, studentCampus);
                return StudentProfile.builder()
                        .id(studentUserId)
                        .studentCode(studentCode)
                        .fullName(studentName)
                        .email(studentEmail)
                        .career(studentCareer)
                        .campus(studentCampus)
                        .currentCycle(studentCycle)
                        .token(accessToken)
                        .enrolledCourseCodes(List.of())
                        .build();
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

                                    registerCourseRef(cleanCourseName, sectionCode, courseUuid, sectionUuid, null);
                                    registerCourseRef(courseNameRaw, sectionCode, courseUuid, sectionUuid, null);

                                    if (!sectionCode.isBlank()) {
                                        putSectionInMap(cleanCourseName, sectionCode);
                                        putSectionInMap(courseNameRaw, sectionCode);
                                    }

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

    private final Map<String, CourseRef> courseRefMap = new ConcurrentHashMap<>();

    private void registerCourseRef(String name, String numericSection, String courseUuid, String sectionUuid, String syllabusUrl) {
        if (name == null || name.isBlank()) return;
        CourseRef ref = new CourseRef(name, numericSection, courseUuid, sectionUuid, syllabusUrl);
        String raw = name.trim().toUpperCase();
        String norm = normalizeSearchKey(raw);
        courseRefMap.put(raw, ref);
        courseRefMap.put(norm, ref);
        if (numericSection != null && !numericSection.isBlank() && numericSection.matches("^\\d+$")) {
            courseRefMap.put(numericSection, ref);
            courseToSectionMap.put(raw, numericSection);
            courseToSectionMap.put(norm, numericSection);
        }
        if (sectionUuid != null && !sectionUuid.isBlank()) {
            courseRefMap.put(sectionUuid.toLowerCase(), ref);
        }
        if (courseUuid != null && !courseUuid.isBlank()) {
            courseRefMap.put(courseUuid.toLowerCase(), ref);
        }
    }

    private CourseRef findCourseRef(String courseCodeOrName) {
        if (courseCodeOrName == null || courseCodeOrName.isBlank()) return null;
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

                        registerCourseRef(pt.cleanTitle, numericSection, courseUuid, sectionUuid, syllabusUrl);
                        registerCourseRef(rawTitle, numericSection, courseUuid, sectionUuid, syllabusUrl);
                        if (!courseId.isBlank()) registerCourseRef(courseId, numericSection, courseUuid, sectionUuid, syllabusUrl);
                    }
                }
            }

            log.info("[UTP PAO Gateway] Mapeadas {} referencias de asignaturas en memoria para descarga de sílabos", courseRefMap.size());
        } catch (Exception e) {
            log.warn("[UTP PAO Gateway] No se pudieron sincronizar secciones desde PAO: {}", e.getMessage());
        }
    }

    private void putSectionInMap(String key, String sectionId) {
        if (key == null || key.isBlank() || sectionId == null || sectionId.isBlank()) return;
        if (!sectionId.matches("^\\d+$")) return; // Solo permitir códigos de sección numéricos oficiales UTP (ej. 45104, 54262, 56357)

        String raw = key.trim().toUpperCase();
        courseToSectionMap.put(raw, sectionId);

        // Clave normalizada sin acentos ni signos
        String normalized = normalizeSearchKey(raw);
        courseToSectionMap.put(normalized, sectionId);
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

                registerCourseRef(parsed.cleanTitle, finalSection, courseUuid, sectionUuid, syllabusUrl);
                registerCourseRef(rawTitle, finalSection, courseUuid, sectionUuid, syllabusUrl);
                if (!courseId.isBlank()) registerCourseRef(courseId, finalSection, courseUuid, sectionUuid, syllabusUrl);

                if (!finalSection.isBlank()) {
                    putSectionInMap(parsed.cleanTitle, finalSection);
                    putSectionInMap(rawTitle, finalSection);
                    if (!courseId.isBlank()) putSectionInMap(courseId, finalSection);
                }

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

        try {
            String pdfUrl = courseCodeOrUrl.trim();

            if (!pdfUrl.startsWith("http://") && !pdfUrl.startsWith("https://")) {
                String userId = extractUserIdFromToken(token);
                String tenantId = extractTenantIdFromToken(token);

                CourseRef ref = findCourseRef(pdfUrl);
                if (ref == null || (ref.syllabusUrl.isBlank() && ref.numericSection.isBlank() && ref.courseUuid.isBlank())) {
                    if (token != null && !token.isBlank()) {
                        populateCourseSectionsFromPao(token);
                        ref = findCourseRef(pdfUrl);
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

                        if (token != null && !token.isBlank()) {
                            reqBuilder.header("authorization", token.startsWith("Bearer ") ? token : "Bearer " + token);
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
}
