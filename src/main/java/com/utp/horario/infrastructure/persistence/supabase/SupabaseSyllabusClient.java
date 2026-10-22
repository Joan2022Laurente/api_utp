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

    /**
     * Backoff tras fallos consecutivos de conectividad con Supabase.
     *
     * <p>Si el proyecto Supabase es inalcanzable (red, DNS, firewall), cada cache-miss
     * repetiría el mismo fallo y saturaría los logs con cientos de ERROR por minuto.
     * Tras {@link #MAX_CONSECUTIVE_FAILURES} fallos consecutivos se aplica una pausa
     * breve antes de reintentar, sin perder la capacidad de recuperación automática.</p>
     */
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final long BACKOFF_MILLIS = 30_000L;

    private final java.util.concurrent.atomic.AtomicInteger consecutiveFailures =
            new java.util.concurrent.atomic.AtomicInteger(0);
    private volatile long backoffUntil = 0L;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Indica si el cliente tiene credenciales utilizables.
     *
     * <p>Sin {@code service-role-key} toda llamada a Supabase sería rechazada con HTTP 401
     * tras agotar el tiempo de espera de red. Se omite la llamada para no penalizar la
     * latencia de los endpoints de sílabo en despliegues sin Supabase configurado.</p>
     */
    private boolean isConfigured() {
        if (!properties.isEnabled()) {
            return false;
        }
        String url = properties.getUrl();
        String key = properties.getServiceRoleKey();
        return url != null && !url.isBlank() && key != null && !key.isBlank();
    }

    /**
     * Determina si el cliente está en ventana de backoff (host inalcanzable).
     */
    private boolean isBackedOff() {
        return System.currentTimeMillis() < backoffUntil;
    }

    private void recordSuccess() {
        if (consecutiveFailures.getAndSet(0) > 0) {
            log.info("[Supabase] Conectividad restaurada con Supabase DB");
        }
    }

    private void recordFailure(String operation, String detail) {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            backoffUntil = System.currentTimeMillis() + BACKOFF_MILLIS;
            log.warn("[Supabase] {} fallo(s) consecutivo(s) en '{}'. Se pausa {} s antes de reintentar. {}",
                    failures, operation, BACKOFF_MILLIS / 1000, detail);
            consecutiveFailures.set(0);
        } else {
            log.error("[Supabase] Error en '{}': {}", operation, detail);
        }
    }

    public Optional<Syllabus> findByCourseCode(String courseCode) {
        if (!isConfigured() || courseCode == null || courseCode.isBlank() || isBackedOff()) {
            return Optional.empty();
        }

        try {
            String clean = courseCode.trim();
            // 1) Coincidencia EXACTA (course_code / course_id / course_name). Es la via
            //    indexada y cubre el caso habitual: el codigo de catalogo o el nombre tal cual.
            String encodedQuery = URLEncoder.encode(
                    "course_code.eq." + clean
                            + ",course_id.eq." + clean
                            + ",course_name.eq." + clean,
                    StandardCharsets.UTF_8);
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
                    Syllabus syllabus = selectBestMatch(root, clean);
                    if (syllabus != null) {
                        log.info("[Supabase] 🎯 Sílabo encontrado en Supabase DB para curso: {} ({})", syllabus.getCourseName(), syllabus.getCourseCode());
                        recordSuccess();
                        return Optional.of(syllabus);
                    }
                }
                // 2) Segunda pasada: emparejamiento insensible a mayusculas sobre el nombre.
                //    Solo se ejecuta si la coincidencia exacta fallo, para no penalizar la
                //    latencia ni el uso de indices en el camino habitual.
                Optional<Syllabus> insensitive = findByCourseNameInsensitive(clean);
                if (insensitive.isPresent()) {
                    recordSuccess();
                    return insensitive;
                }
                // 3) Tercera pasada: emparejamiento IGNORANDO ACENTOS. Ni 'eq' ni 'ilike'
                //    de PostgreSQL ignoran los diacriticos, por lo que un nombre como
                //    "PROGRAMACION" no encuentra "PROGRAMACIÓN". Se resuelve comparando
                //    localmente el nombre normalizado de la fila almacenada.
                Optional<Syllabus> normalized = findByNormalizedCourseName(clean);
                if (normalized.isPresent()) {
                    recordSuccess();
                    return normalized;
                }
                recordSuccess();
            } else {
                log.warn("[Supabase] Error consultando curso {}: HTTP {}", clean, response.statusCode());
            }
        } catch (Exception e) {
            recordFailure("findByCourseCode(" + courseCode + ")", String.valueOf(e.getMessage()));
        }

        return Optional.empty();
    }

    /**
     * Busca un sílabo por nombre de curso ignorando mayusculas y minusculas.
     *
     * <p>Usa {@code ilike} de PostgREST y valida la coincidencia en Java, porque
     * {@code ilike} trata {@code _} como comodin y podría devolver filas que no
     * corresponden exactamente al nombre solicitado.</p>
     */
    private Optional<Syllabus> findByCourseNameInsensitive(String clean) {
        try {
            String encodedQuery = URLEncoder.encode("course_name.ilike." + clean, StandardCharsets.UTF_8);
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
                    Syllabus syllabus = selectBestMatch(root, clean);
                    if (syllabus != null) {
                        log.info("[Supabase] 🎯 Sílabo encontrado en Supabase DB por nombre (sin distinguir mayúsculas): {} ({})",
                                syllabus.getCourseName(), syllabus.getCourseCode());
                        return Optional.of(syllabus);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[Supabase] Búsqueda insensible a mayúsculas sin resultado para '{}': {}", clean, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Busca un sílabo por nombre de curso ignorando mayusculas <b>y acentos</b>.
     *
     * <p>PostgreSQL no ofrece una comparación «accent-insensitive» ni en {@code eq} ni en
     * {@code ilike}, por lo que un nombre como "PROGRAMACION" no localiza "PROGRAMACIÓN".
     * La tabla de sílabos es pequeña, asi que se listan sus identificadores y se comparan
     * localmente con el nombre normalizado.</p>
     */
    private Optional<Syllabus> findByNormalizedCourseName(String clean) {
        String target = normalizeCourseName(clean);
        if (target.isBlank()) {
            return Optional.empty();
        }
        try {
            String url = properties.getUrl() + "/rest/v1/" + properties.getTable()
                    + "?select=course_code,course_name";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("apikey", properties.getServiceRoleKey())
                    .header("Authorization", "Bearer " + properties.getServiceRoleKey())
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode rows = objectMapper.readTree(response.body());
            if (!rows.isArray()) {
                return Optional.empty();
            }
            for (JsonNode row : rows) {
                String storedName = row.path("course_name").asText("");
                if (!storedName.isBlank() && normalizeCourseName(storedName).equals(target)) {
                    String courseCode = row.path("course_code").asText("");
                    if (!courseCode.isBlank()) {
                        Optional<Syllabus> resolved = findByCourseCode(courseCode);
                        if (resolved.isPresent()) {
                            log.info("[Supabase] 🎯 Sílabo localizado por nombre normalizado (sin acentos): '{}' -> {}",
                                    clean, courseCode);
                            return resolved;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[Supabase] Búsqueda por nombre normalizado sin resultado para '{}': {}", clean, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Normaliza un nombre de curso para comparaciones: sin acentos, sin mayusculas y sin
     * espacios redundantes.
     */
    static String normalizeCourseName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]+", " ")
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
        return normalized;
    }

    /**
     * Selecciona la fila que mejor corresponde a la clave consultada.
     *
     * <p>Prioridad: coincidencia exacta de codigo (course_code / course_id) y, en su defecto,
     * coincidencia exacta del nombre ignorando mayusculas. La validacion final se hace en
     * Java porque {@code ilike} trata {@code _} como comodin y podria devolver filas
     * que no corresponden exactamente a la clave solicitada.</p>
     */
    private Syllabus selectBestMatch(JsonNode rows, String clean) {
        String key = clean.trim().toLowerCase(java.util.Locale.ROOT);
        Syllabus byName = null;
        for (JsonNode row : rows) {
            Syllabus candidate = mapRowToSyllabus(row);
            String code = candidate.getCourseCode() != null
                    ? candidate.getCourseCode().trim().toLowerCase(java.util.Locale.ROOT) : "";
            String id = candidate.getId() != null
                    ? candidate.getId().trim().toLowerCase(java.util.Locale.ROOT) : "";
            String name = candidate.getCourseName() != null
                    ? candidate.getCourseName().trim().toLowerCase(java.util.Locale.ROOT) : "";
            if (code.equals(key) || id.equals(key)) {
                return candidate;
            }
            if (byName == null && name.equals(key)) {
                byName = candidate;
            }
        }
        return byName;
    }

    public List<Syllabus> findAll() {
        List<Syllabus> list = new ArrayList<>();
        if (!isConfigured() || isBackedOff()) {
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
                recordSuccess();
            }
        } catch (Exception e) {
            recordFailure("findAll", String.valueOf(e.getMessage()));
        }

        return list;
    }

    public Syllabus upsert(Syllabus syllabus) {
        if (!isConfigured() || syllabus == null || isBackedOff()) {
            return syllabus;
        }

        try {
            String courseId = (syllabus.getCourseCode() != null && !syllabus.getCourseCode().isBlank())
                    ? syllabus.getCourseCode()
                    : syllabus.getId();

            // Upsert IDEMPOTENTE: si el contenido almacenado es equivalente al nuevo no se
            // reescribe la fila, de modo que 'updated_at' sigue siendo el indicador real de
            // la ultima regeneracion y se evitan escrituras innecesarias en produccion.
            Optional<Syllabus> current = findByCourseCode(courseId);
            if (current.isPresent() && isEquivalentContent(current.get(), syllabus)) {
                log.debug("[Supabase] Sílabo {} sin cambios: no se reescribe la fila", courseId);
                return syllabus;
            }

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
            // Sílabo completo: permite una lectura 1:1 sin perdidas de campos.
            // Es opcional: si la columna no existe en el esquema, se reintenta sin ella.
            payload.put("raw_json", toRawJson(syllabus));
            payload.put("careers", syllabus.getCareers() != null ? syllabus.getCareers() : List.of());
            payload.put("semester", syllabus.getSemester());
            payload.put("ai_policy", syllabus.getAiPolicy());
            payload.put("max_similarity_percent", syllabus.getMaxSimilarityPercent());

            String url = properties.getUrl() + "/rest/v1/" + properties.getTable();

            int status = postPayload(url, payload);
            if (status >= 400) {
                // El esquema puede no tener las columnas opcionales: se reintenta con el
                // conjunto base, que todas las instalaciones tienen.
                Map<String, Object> reduced = new LinkedHashMap<>(payload);
                for (String optional : new String[]{"raw_json", "careers", "semester", "ai_policy", "max_similarity_percent"}) {
                    reduced.remove(optional);
                }
                status = postPayload(url, reduced);
            }
            if (status == 200 || status == 201) {
                log.info("[Supabase] 💾 Sílabo persistido exitosamente en Supabase DB para: {} ({})",
                        syllabus.getCourseName(), syllabus.getCourseCode());
            } else {
                log.warn("[Supabase] Respuesta no esperada al persistir {}: HTTP {}", courseId, status);
            }
        } catch (Exception e) {
            recordFailure("upsert(" + syllabus.getCourseCode() + ")", String.valueOf(e.getMessage()));
        }

        return syllabus;
    }

    /**
     * Compara el contenido relevante de dos silabos para decidir si la fila debe reescribirse.
     *
     * <p>Se ignoran los campos que solo dependen del formato dealmacenamiento (por ejemplo
     * el desglose de 'topics' frente a 'topic'), de modo que una fila generada por la IA y
     * la regenerada por el motor determinista se consideran equivalentes.</p>
     */
    private boolean isEquivalentContent(Syllabus stored, Syllabus incoming) {
        if (stored == null || incoming == null) {
            return false;
        }
        return equalText(stored.getFormula(), incoming.getFormula())
                && equalText(stored.getLearningGoal(), incoming.getLearningGoal())
                && equalText(stored.getModality(), incoming.getModality())
                && equalText(stored.getCourseName(), incoming.getCourseName())
                && equalText(stored.getCredits(), incoming.getCredits())
                && equalText(stored.getWeeklyHours(), incoming.getWeeklyHours())
                && equalText(stored.getRules(), incoming.getRules())
                && equalEvaluations(stored.getEvaluations(), incoming.getEvaluations())
                && equalSchedule(stored.getWeeklySchedule(), incoming.getWeeklySchedule());
    }

    private boolean equalText(Object a, Object b) {
        return String.valueOf(a == null ? "" : a).equals(String.valueOf(b == null ? "" : b));
    }

    private boolean equalEvaluations(List<SyllabusEvaluation> a, List<SyllabusEvaluation> b) {
        List<SyllabusEvaluation> left = a == null ? List.of() : a;
        List<SyllabusEvaluation> right = b == null ? List.of() : b;
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            SyllabusEvaluation l = left.get(i);
            SyllabusEvaluation r = right.get(i);
            if (!equalText(l.getType(), r.getType())
                    || !equalText(l.getDescription(), r.getDescription())
                    || !equalText(l.getWeek(), r.getWeek())
                    || !equalText(l.getWeightPercent(), r.getWeightPercent())
                    || !equalText(l.getModality(), r.getModality())) {
                return false;
            }
        }
        return true;
    }

    private boolean equalSchedule(List<SyllabusWeeklySession> a, List<SyllabusWeeklySession> b) {
        List<SyllabusWeeklySession> left = a == null ? List.of() : a;
        List<SyllabusWeeklySession> right = b == null ? List.of() : b;
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            SyllabusWeeklySession l = left.get(i);
            SyllabusWeeklySession r = right.get(i);
            if (!equalText(l.getWeek(), r.getWeek())
                    || !equalText(l.getUnit(), r.getUnit())
                    || !equalText(l.getTopic(), r.getTopic())
                    || !equalText(l.getEvaluation(), r.getEvaluation())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Serializa el sílabo completo para la columna {@code raw_json}.
     */
    private String toRawJson(Syllabus syllabus) {
        try {
            return objectMapper.writeValueAsString(syllabus);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Ejecuta el POST de un payload y devuelve el codigo HTTP de la respuesta.
     */
    private int postPayload(String url, Map<String, Object> payload) throws Exception {
        String jsonPayload = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("apikey", properties.getServiceRoleKey())
                .header("Authorization", "Bearer " + properties.getServiceRoleKey())
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
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
        // Si la fila trae el silabo completo serializado (columna raw_json), se reconstruye
        // desde el para que la lectura sea 1:1 y no se pierda ningun campo del modelo.
        JsonNode raw = row.path("raw_json");
        if (raw.isObject() && !raw.isEmpty()) {
            try {
                return objectMapper.treeToValue(raw, Syllabus.class);
            } catch (Exception e) {
                log.debug("[Supabase] No se pudo leer raw_json; se usa el mapeo por columnas: {}", e.getMessage());
            }
        }

        String courseId = row.path("course_id").asText("");
        String courseCode = row.path("course_code").asText(courseId);
        String courseName = row.path("course_name").asText("");
        int credits = row.path("credits").asInt(3);
        String hoursStr = row.path("hours").asText("4h").replaceAll("[^0-9]", "");
        Integer weeklyHours = hoursStr.isEmpty() ? 4 : Integer.parseInt(hoursStr);
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

        // Reglas y politica academica: columnas existentes en la tabla.
        List<String> rules = new ArrayList<>();
        JsonNode rulesNode = row.path("rules");
        if (rulesNode.isArray()) {
            for (JsonNode rn : rulesNode) {
                rules.add(rn.asText());
            }
        }
        String aiPolicy = row.path("ai_policy").asText(row.path("anti_plagiarism_policy").asText(""));

        return Syllabus.builder()
                .id(courseId)
                .courseCode(courseCode)
                .courseName(courseName)
                .credits(credits)
                .weeklyHours(weeklyHours)
                .modality(modality)
                .formula(formula)
                .learningGoal(learningGoal)
                .semester(row.path("semester").asText(null))
                .careers(readStringArray(row.path("careers")))
                .rules(rules)
                .aiPolicy(aiPolicy.isBlank() ? null : aiPolicy)
                .maxSimilarityPercent(row.hasNonNull("max_similarity_percent")
                        ? row.path("max_similarity_percent").asInt() : null)
                .evaluations(evaluations)
                .weeklySchedule(weeklySessions)
                .build();
    }

    /**
     * Lee un array jsonb de cadenas, tolerando valores nulos.
     */
    private List<String> readStringArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                values.add(item.asText());
            }
        }
        return values;
    }
}
