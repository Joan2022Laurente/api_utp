package com.utp.horario.infrastructure.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.exception.AuthenticationException;
import com.utp.horario.domain.model.ClassSession;
import com.utp.horario.domain.model.Course;
import com.utp.horario.domain.model.ModalityType;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.model.ScheduleItemParsingResult;
import com.utp.horario.infrastructure.external.schedule.ScheduleItemParser;
import com.utp.horario.support.FakeHttpClient;
import com.utp.horario.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regresiones de los fallbacks erradicados en Fase 1 (F1/F2/F3) y del blindaje del
 * catálogo de edificios (Checkpoint §16).
 */
class UtpGatewayDefensiveFallbacksTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("F1: un JWT sin claims de tenant resuelve el tenant institucional centralizado de la UTP")
    void tokenWithoutTenantClaimResolvesInstitutionalTenant() throws Exception {
        String token = TestTokens.build("U99999999",
                System.currentTimeMillis() / 1000 + 3600, Map.of());
        // El helper inyecta tenantId por defecto: lo eliminamos explícitamente del payload.
        String[] parts = token.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        for (String claim : new String[]{"tenantId", "tenant_id", "tenant", "x-tenant-id"}) {
            payload = payload.replaceAll(",\"" + claim + "\":\"[^\"]*\"", "");
        }
        String tenantLess = parts[0] + "."
                + java.util.Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + "." + parts[2];

        UtpPortalGatewayAdapter adapter = new UtpPortalGatewayAdapter(FakeHttpClient.always(200, "{}"));

        String resolvedTenant = adapter.extractTenantIdFromToken(tenantLess);
        assertEquals(UtpPortalGatewayAdapter.CANONICAL_UTP_TENANT_ID, resolvedTenant);
    }

    @Test
    @DisplayName("F1-B: un JWT con claim de tenant explícito respeta y prioriza el valor del claim")
    void tokenWithExplicitTenantClaimIsHonored() throws Exception {
        String customTenant = "99999999-9999-5999-8999-999999999999";
        String token = TestTokens.build("U99999999",
                System.currentTimeMillis() / 1000 + 3600, Map.of("tenantId", customTenant));

        UtpPortalGatewayAdapter adapter = new UtpPortalGatewayAdapter(FakeHttpClient.always(200, "{}"));

        String resolvedTenant = adapter.extractTenantIdFromToken(token);
        assertEquals(customTenant, resolvedTenant);
    }

    @Test
    @DisplayName("F2: evento sin aula ni edificio deja classroom/building/teacher en null tipado")
    void missingClassroomYieldsNullsWithoutSentinelTexts() throws Exception {
        var item = MAPPER.readTree("""
                {"name":"CURSO SIN DATOS","period":"2263","class":{
                  "id":"gql-no-room","start":1790033400000,"end":1790038400000,
                  "instructionMode":"P","location":{}}}
                """);

        ScheduleItemParsingResult result = new ScheduleItemParser(Map.of(), Map.of(), Map.of()).parse(item);
        ClassSession session = assertInstanceOf(ScheduleItemParsingResult.Session.class, result).session();

        assertNull(session.getClassroom());
        assertNull(session.getBuilding());
        assertNull(session.getBuildingId());
        assertNull(session.getTeacher());
        assertEquals("CURSO SIN DATOS", session.getCourseName());
    }

    @Test
    @DisplayName("F3: si UTP no entrega créditos ni horas, Course los conserva en null")
    void missingCreditsAndWeeklyHoursStayNull() {
        FakeHttpClient routing = new FakeHttpClient(request -> {
            String uri = request.uri().toString();
            if (uri.contains("/dashboard-courses")) {
                return new FakeHttpClient.Response(200, """
                        {"data":[{"sectionId":"sec-1","courseCode":"100000SI97",
                          "classNumber":"34374","sectionCode":"2263100000SI9734374",
                          "period":"2263","name":"SERVICIOS CLOUD","modality":"P"}]}
                        """);
            }
            if (uri.contains("/academicperiods")) {
                return new FakeHttpClient.Response(200,
                        "[{\"id\":\"2263\",\"code\":\"2026-2\",\"name\":\"2026 - Ciclo 2 Agosto\",\"isCurrent\":true,\"startDate\":\"2026-08-15\",\"endDate\":\"2026-12-13\"}]");
            }
            return new FakeHttpClient.Response(200, """
                    {"data":{"scheduleByDate":{"dates":[{"date":"2026-09-21","items":[
                      {"name":"SERVICIOS CLOUD","period":"2263","class":{
                        "id":"c1","start":1790033400000,"end":1790038400000,
                        "instructionMode":"P","classType":"1",
                        "linkCourseClass":"https://class.utp.edu.pe/student/courses/x/section/sec-1/learnv2",
                        "location":{"classRoom":{"id":"A0909"},"building":{"id":"SL04_PACIF"}}}}
                    ]}]}}}
                    """);
        });

        ScheduleInterval interval = new UtpPortalGatewayAdapter(routing)
                .fetchSchedule(TestTokens.validFor("U23307609"), "2263");

        assertTrue(interval.getCourses().stream().anyMatch(c ->
                c.getCredits() == null && c.getWeeklyHours() == null),
                "Ningún curso debe recibir créditos/horas inventados");
    }

    @Test
    @DisplayName("Edificio no resoluble: conserva el id crudo SL04_PACIF y nombre null")
    void unresolvedBuildingKeepsRawId() throws Exception {
        var item = MAPPER.readTree("""
                {"name":"SERVICIOS CLOUD","period":"2263","class":{
                  "id":"c1","start":1790033400000,"end":1790038400000,
                  "instructionMode":"P","classType":"1",
                  "location":{"classRoom":{"id":"A0909"},"building":{"id":"SL04_PACIF","desc":null,"address":null}}}}
                """);

        ScheduleItemParsingResult result = new ScheduleItemParser(Map.of(), Map.of(), Map.of()).parse(item);
        ClassSession session = assertInstanceOf(ScheduleItemParsingResult.Session.class, result).session();

        assertEquals("SL04_PACIF", session.getBuildingId());
        assertNull(session.getBuilding());
    }

    @Test
    @DisplayName("Checkpoint Ruptura 2: modality.id manda sobre instructionMode y startTime es fuente válida")
    void checkpointHierarchicalFieldsAreHonored() throws Exception {
        var item = MAPPER.readTree("""
                {"name":"HERRAMIENTAS","period":"2263","modality":{"id":"VT"},
                 "startTime":1790033400000,"endTime":1790038400000,
                 "class":{"id":"c-vt","instructionMode":"P","classType":"1"}}
                """);

        ScheduleItemParsingResult result = new ScheduleItemParser(Map.of(), Map.of(), Map.of()).parse(item);
        assertInstanceOf(ScheduleItemParsingResult.AsyncCourseRef.class, result);
    }

    @Test
    @DisplayName("Checkpoint §8.8: descAmb y professors como lista se parsean sin inventar datos")
    void descAmbAndProfessorArrayAreParsed() throws Exception {
        var item = MAPPER.readTree("""
                {"name":"SERVICIOS CLOUD","period":"2263",
                 "class":{"id":"c1","start":1790033400000,"end":1790038400000,
                   "instructionMode":"R","classType":"5","descAmb":"AULA VIRTUAL",
                   "professors":[{"firstName":"MATIAS","lastName":"PALOMINO"}]}}
                """);

        ClassSession session = assertInstanceOf(ScheduleItemParsingResult.Session.class,
                new ScheduleItemParser(Map.of(), Map.of(), Map.of()).parse(item)).session();

        assertEquals("AULA VIRTUAL", session.getEnvironmentType());
        assertEquals("MATIAS PALOMINO", session.getTeacher());
    }

    @Test
    @DisplayName("Checkpoint 4: un curso VT del dashboard es asíncrono aunque el item no lo marque")
    void dashboardModalityAlsoForcesAsync() throws Exception {
        var item = MAPPER.readTree("""
                {"name":"RUTA LABORAL","period":"2263",
                 "class":{"id":"c-vt","start":1790033400000,"end":1790038400000,
                   "instructionMode":"P","classType":"1"}}
                """);
        Course vt = Course.builder().name("RUTA LABORAL").code("100000EMP1")
                .modalityType(ModalityType.ASYNC_VIRTUAL).build();

        ScheduleItemParsingResult result = new ScheduleItemParser(
                Map.of(), Map.of("RUTALABORAL", vt), Map.of()).parse(item);
        assertInstanceOf(ScheduleItemParsingResult.AsyncCourseRef.class, result);
    }

    @Test
    @DisplayName("Ruptura 4: actividad resuelve courseCode por sectionId, jamás por courseName")
    void activitiesNeverJoinByCourseName() {
        FakeHttpClient routing = new FakeHttpClient(request -> {
            String uri = request.uri().toString();
            if (uri.contains("/dashboard-courses")) {
                return new FakeHttpClient.Response(200, """
                        {"data":[
                          {"sectionId":"sec-real","courseCode":"100000SI97","classNumber":"11111",
                           "sectionCode":"2263100000SI9711111","period":"2263","name":"NOMBRE REAL","modality":"P"},
                          {"sectionId":"sec-otro","courseCode":"100000ST61","classNumber":"22222",
                           "sectionCode":"2263100000ST6122222","period":"2263","name":"NOMBRE INVENTADO","modality":"P"}
                        ]}
                        """);
            }
            return new FakeHttpClient.Response(200, """
                    {"data":{"current_interval":{"events":[{
                      "id":"act-1","title":"Tarea","type":"ACTIVITY",
                      "metadata":{"sectionId":"sec-real","courseName":"NOMBRE INVENTADO",
                                  "activityType":"HOMEWORK","studentStatus":"PENDING"}
                    }]}}}
                    """);
        });

        var activities = new UtpPortalGatewayAdapter(routing).fetchCalendarActivities(
                "2026-09-21", "period", TestTokens.validFor("U23307609"));

        assertEquals(1, activities.size());
        assertEquals("100000SI97", activities.get(0).getCourseCode(),
                "El courseCode debe resolverse por sectionId, no por el nombre del evento");
    }
}
