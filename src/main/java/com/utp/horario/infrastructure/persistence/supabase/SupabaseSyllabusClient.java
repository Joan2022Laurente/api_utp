package com.utp.horario.infrastructure.persistence.supabase;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import com.utp.horario.infrastructure.config.supabase.SupabaseProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupabaseSyllabusClient {

    private final SupabaseProperties properties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public Optional<Syllabus> findByCourseCode(String courseCode) {
        if (!properties.isEnabled() || courseCode == null || courseCode.isBlank()) {
            return Optional.empty();
        }

        try {
            String clean = courseCode.trim();
            String encodedQuery = URLEncoder.encode("course_code.eq." + clean + ",course_id.eq." + clean, StandardCharsets.UTF_8);
            String url = properties.getUrl() + "/rest/v1/" + properties.getTable() + "?or=(" + encodedQuery + ")&select=*";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("apikey", properties.getServiceRoleKey())
                    .header("Authorization", "Bearer " + properties.getServiceRoleKey())
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.isArray() && !root.isEmpty()) {
                    JsonNode row = root.get(0);
                    Syllabus syllabus = mapRowToSyllabus(row);
                    log.info("[Supabase] 🎯 Sílabo encontrado en Supabase DB para curso: {} ({})", syllabus.getCourseName(), syllabus.getCourseCode());
                    return Optional.of(syllabus);
                }
            } else {
                log.warn("[Supabase] Error consultando curso {}: HTTP {}", clean, response.statusCode());
            }
        } catch (Exception e) {
            log.error("[Supabase] Excepción al consultar sílabo en Supabase para {}: {}", courseCode, e.getMessage());
        }

        return Optional.empty();
    }

    public List<Syllabus> findAll() {
        List<Syllabus> list = new ArrayList<>();
        if (!properties.isEnabled()) {
            return list;
        }

        try {
            String url = properties.getUrl() + "/rest/v1/" + properties.getTable() + "?select=*";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("apikey", properties.getServiceRoleKey())
                    .header("Authorization", "Bearer " + properties.getServiceRoleKey())
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.isArray()) {
                    for (JsonNode row : root) {
                        list.add(mapRowToSyllabus(row));
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Supabase] Error listando sílabos desde Supabase: {}", e.getMessage());
        }

        return list;
    }

    public Syllabus upsert(Syllabus syllabus) {
        if (!properties.isEnabled() || syllabus == null) {
            return syllabus;
        }

        try {
            String courseId = (syllabus.getCourseCode() != null && !syllabus.getCourseCode().isBlank()) 
                    ? syllabus.getCourseCode() 
                    : syllabus.getId();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("course_id", courseId);
            payload.put("course_code", syllabus.getCourseCode());
            payload.put("course_name", syllabus.getCourseName());
            payload.put("credits", syllabus.getCredits() != null ? syllabus.getCredits() : 3);
            payload.put("hours", syllabus.getWeeklyHours() != null ? (syllabus.getWeeklyHours() + "h") : "4h");
            payload.put("modality", syllabus.getModality() != null ? syllabus.getModality() : "Presencial");
            payload.put("formula", syllabus.getFormula());
            payload.put("learning_goal", syllabus.getLearningGoal());
            payload.put("evaluations", syllabus.getEvaluations() != null ? syllabus.getEvaluations() : List.of());
            payload.put("weekly_schedule", mapScheduleToSupabaseFormat(syllabus.getWeeklySchedule()));
            payload.put("rules", syllabus.getRules() != null ? syllabus.getRules() : List.of());
            payload.put("updated_at", OffsetDateTime.now().toString());

            String jsonPayload = objectMapper.writeValueAsString(payload);
            String url = properties.getUrl() + "/rest/v1/" + properties.getTable();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("apikey", properties.getServiceRoleKey())
                    .header("Authorization", "Bearer " + properties.getServiceRoleKey())
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=merge-duplicates,return=representation")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                log.info("[Supabase] 💾 Sílabo persistido exitosamente en Supabase DB para: {} ({})", 
                        syllabus.getCourseName(), syllabus.getCourseCode());
            } else {
                log.warn("[Supabase] Respuesta no esperada al persistir {}: HTTP {} -> {}", 
                        courseId, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("[Supabase] Error persistiendo sílabo en Supabase para {}: {}", syllabus.getCourseCode(), e.getMessage());
        }

        return syllabus;
    }

    private List<Map<String, Object>> mapScheduleToSupabaseFormat(List<SyllabusWeeklySession> sessions) {
        if (sessions == null) return List.of();
        List<Map<String, Object>> list = new ArrayList<>();
        for (SyllabusWeeklySession s : sessions) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("week", s.getWeek());
            map.put("unit", s.getUnit());
            map.put("topics", s.getTopic() != null ? List.of(s.getTopic()) : List.of());
            map.put("activities", s.getActivities() != null ? s.getActivities() : "");
            map.put("evaluation", s.getEvaluation());
            map.put("isDeliverableForClassScore", s.getEvaluation() != null && !s.getEvaluation().isBlank());
            list.add(map);
        }
        return list;
    }

    private Syllabus mapRowToSyllabus(JsonNode row) {
        String courseId = row.path("course_id").asText("");
        String courseCode = row.path("course_code").asText(courseId);
        String courseName = row.path("course_name").asText("");
        int credits = row.path("credits").asInt(3);
        String modality = row.path("modality").asText("Presencial");
        String formula = row.path("formula").asText("");
        String learningGoal = row.path("learning_goal").asText("");

        // Weekly schedule mapping
        List<SyllabusWeeklySession> weeklySessions = new ArrayList<>();
        JsonNode scheduleNode = row.path("weekly_schedule");
        if (scheduleNode.isArray()) {
            for (JsonNode sn : scheduleNode) {
                int week = sn.path("week").asInt(1);
                String unit = sn.path("unit").asText("");
                String activities = sn.path("activities").asText("");
                String eval = sn.hasNonNull("evaluation") ? sn.path("evaluation").asText() : null;

                String topic = "";
                JsonNode topicsNode = sn.path("topics");
                if (topicsNode.isArray() && !topicsNode.isEmpty()) {
                    List<String> tList = new ArrayList<>();
                    for (JsonNode t : topicsNode) {
                        tList.add(t.asText());
                    }
                    topic = String.join(", ", tList);
                } else if (sn.hasNonNull("topic")) {
                    topic = sn.path("topic").asText();
                }

                weeklySessions.add(SyllabusWeeklySession.builder()
                        .week(week)
                        .session(1)
                        .unit(unit)
                        .topic(topic)
                        .activities(activities)
                        .evaluation(eval)
                        .build());
            }
        }

        // Evaluations mapping
        List<SyllabusEvaluation> evaluations = new ArrayList<>();
        JsonNode evalsNode = row.path("evaluations");
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

        return Syllabus.builder()
                .id(courseId)
                .courseCode(courseCode)
                .courseName(courseName)
                .credits(credits)
                .modality(modality)
                .formula(formula)
                .learningGoal(learningGoal)
                .evaluations(evaluations)
                .weeklySchedule(weeklySessions)
                .build();
    }
}
