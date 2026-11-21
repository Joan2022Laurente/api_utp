package com.utp.horario.infrastructure.external.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.utp.horario.domain.model.ClassSession;
import com.utp.horario.domain.model.Course;
import com.utp.horario.domain.model.ScheduleItemParsingResult;
import com.utp.horario.domain.policy.AsynchronousSessionPolicy;
import com.utp.horario.domain.util.CourseNameNormalizer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser tipado de cada ítem de {@code scheduleByDate}.
 *
 * <p>DIRECTIVA: CERO HARDCODED DATA &amp; CERO FALLBACKS ENGAÑOSOS. Todo dato que UTP no
 * entrega se deja como {@code null} tipado. Nunca se inventan docente, aula, edificio ni
 * título. Los timestamps corruptos se rechazan explícitamente para no emitir sesiones
 * "zombi" en 1970.</p>
 */
public final class ScheduleItemParser {

    private static final Pattern SECTION_UUID_PATTERN =
            Pattern.compile("/section/([a-f0-9\\-]{36})/learnv2", Pattern.CASE_INSENSITIVE);

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    /** Catálogo de cursos enriquecido, indexado por sectionId (UUID Canvas). */
    private final Map<String, Course> sectionCatalog;

    /** Catálogo por nombre normalizado (respaldo del Checkpoint 1 cuando no hay sectionId). */
    private final Map<String, Course> nameCatalog;

    /** Catálogo dinámico de edificios resuelto desde {@code getBuildings}. */
    private final Map<String, String> buildingCatalog;

    public ScheduleItemParser(Map<String, Course> sectionCatalog, Map<String, Course> nameCatalog,
                              Map<String, String> buildingCatalog) {
        this.sectionCatalog = sectionCatalog;
        this.nameCatalog = nameCatalog;
        this.buildingCatalog = buildingCatalog;
    }

    public ScheduleItemParsingResult parse(JsonNode item) {
        if (item == null || !item.hasNonNull("class")) {
            return new ScheduleItemParsingResult.Rejected("Nodo 'class' ausente o nulo en el payload");
        }
        JsonNode cls = item.get("class");

        // Ruptura 2: jerarquía oficial item.modality.id -> class.instructionMode -> null.
        String itemModality = item.hasNonNull("modality") ? text(item.get("modality"), "id") : null;
        String instructionMode = firstNonBlank(itemModality, text(cls, "instructionMode"));
        String classType = text(cls, "classType");
        String classLink = text(cls, "linkCourseClass");
        String sectionId = extractSectionId(classLink);
        String name = firstNonBlank(text(item, "name"), text(cls, "name"));
        Course matched = (sectionId != null) ? sectionCatalog.get(sectionId) : null;
        if (matched == null && name != null) {
            matched = nameCatalog.get(CourseNameNormalizer.normalize(name));
        }
        if (matched != null && sectionId == null) {
            sectionId = matched.getSectionId();
        }
        String courseCode = matched != null ? matched.getCode() : null;

        // 1. Invariante canónica de asincronía (VT o classType 3/4) + refuerzo del
        // Checkpoint 4: la modalidad institucional del dashboard también manda.
        boolean dashboardAsync = matched != null && matched.getModalityType() != null
                && matched.getModalityType().isAsyncVirtual();
        if (dashboardAsync || AsynchronousSessionPolicy.isAsynchronous(instructionMode, classType)) {
            return new ScheduleItemParsingResult.AsyncCourseRef(courseCode, sectionId, name, classLink);
        }

        // 2. Tiempos con validación estricta de rango (startEpoch > 0, endEpoch > startEpoch).
        Long startEpoch = firstEpoch(item, cls, "startTime", "start");
        Long endEpoch = firstEpoch(item, cls, "endTime", "end");
        if (startEpoch == null || endEpoch == null
                || startEpoch <= 0L || endEpoch <= 0L || startEpoch >= endEpoch) {
            return new ScheduleItemParsingResult.Rejected(String.format(
                    "Marcas temporales inválidas o cronología invertida (start=%s, end=%s)",
                    startEpoch, endEpoch));
        }

        // 3. Datos puros; ausencia = null, nunca texto sentinela.
        String teacher = null;
        JsonNode professors = cls.get("professors");
        if (professors != null && professors.isArray() && !professors.isEmpty()) {
            professors = professors.get(0);
        }
        if (professors != null && professors.isObject()) {
            teacher = joinNames(text(professors, "firstName"), text(professors, "lastName"));
        }

        String classroomId = null;
        String buildingId = null;
        JsonNode location = cls.get("location");
        if (location != null) {
            JsonNode room = location.get("classRoom");
            if (room != null) {
                classroomId = text(room, "id");
            }
            JsonNode building = location.get("building");
            if (building != null) {
                buildingId = text(building, "id");
            }
        }
        // Resolución dinámica: si no está en el catálogo, se retiene el id crudo y el
        // nombre queda null (nunca "Pabellón UTP" ni textos inventados).
        String buildingName = buildingId != null ? buildingCatalog.get(buildingId) : null;
        String floor = null;
        if (location != null && location.hasNonNull("classRoom")) {
            floor = text(location.get("classRoom"), "floor");
        }

        // Si la API GraphQL no devuelve un 'id' oficial, se deja null: nunca se fabrica un identificador sintético.
        String id = text(cls, "id");
        LocalDateTime startAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(startEpoch), LIMA);
        LocalDateTime finishAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(endEpoch), LIMA);

        ClassSession session = ClassSession.builder()
                .id(id)
                .courseCode(courseCode)
                .courseName(name)
                .section(matched != null ? matched.getSection() : null)
                .sectionId(sectionId)
                .modalityType(matched != null ? matched.getModalityType() : null)
                .classroom(classroomId)
                .building(buildingName)
                .buildingId(buildingId)
                .teacher(teacher)
                .modality(instructionMode)
                .startAt(startAt)
                .finishAt(finishAt)
                .zoomLink(text(cls, "linkZoom"))
                .classLink(classLink)
                .floor(floor)
                .environmentType(text(cls, "descAmb"))
                .build();

        return new ScheduleItemParsingResult.Session(session);
    }

    private static String extractSectionId(String linkCourseClass) {
        if (linkCourseClass == null) {
            return null;
        }
        Matcher matcher = SECTION_UUID_PATTERN.matcher(linkCourseClass);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Long firstEpoch(JsonNode item, JsonNode cls, String itemKey, String classKey) {
        Long value = epoch(item, itemKey);
        return value != null ? value : epoch(cls, classKey);
    }

    private static Long epoch(JsonNode node, String key) {
        if (node == null || !node.hasNonNull(key)) {
            return null;
        }
        JsonNode v = node.get(key);
        return v.isNumber() ? v.asLong() : null;
    }

    private static String firstNonBlank(String a, String b) {
        String first = (a != null && !a.isBlank()) ? a.trim() : null;
        if (first != null) {
            return first;
        }
        return (b != null && !b.isBlank()) ? b.trim() : null;
    }

    private static String joinNames(String first, String last) {
        String f = (first != null && !first.isBlank()) ? first.trim() : "";
        String l = (last != null && !last.isBlank()) ? last.trim() : "";
        String full = (f + " " + l).trim();
        return full.isEmpty() ? null : full;
    }

    private static String text(JsonNode node, String key) {
        if (node == null || !node.hasNonNull(key)) {
            return null;
        }
        String value = node.get(key).asText();
        return value.isBlank() ? null : value.trim();
    }
}
