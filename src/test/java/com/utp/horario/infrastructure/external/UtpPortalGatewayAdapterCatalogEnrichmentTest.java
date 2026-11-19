package com.utp.horario.infrastructure.external;

import com.utp.horario.domain.exception.UpstreamServiceException;
import com.utp.horario.domain.model.ClassSession;
import com.utp.horario.domain.model.ModalityType;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.support.FakeHttpClient;
import com.utp.horario.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regresiones de la bitácora `laboratorio/REVERSE_ENGINEERING_LOG.md` (checkpoints 1-4):
 * código de catálogo resuelto desde el dashboard, identidad de sección preservada y
 * modalidad VT sin franjas sincrónicas.
 */
class UtpPortalGatewayAdapterCatalogEnrichmentTest {

    private static final String TOKEN = TestTokens.validFor("U23307609");
    private static final String ASYNC_SECTION = "83a80520-fc23-54ca-aa94-471582761354";
    private static final String LIVE_SECTION = "9fc26189-8030-5263-a048-96a2290effa4";

    private static final String DASHBOARD = """
            {"data":[
              {"sectionId":"83a80520-fc23-54ca-aa94-471582761354",
               "courseCode":"100000S72V","classNumber":"54262",
               "sectionCode":"2263100000S72V54262",
               "name":"HERRAMIENTAS PARA LA COMUNICACIÓN EFECTIVA","modality":"VT"},
              {"sectionId":"9fc26189-8030-5263-a048-96a2290effa4",
               "courseCode":"100000S74T","classNumber":"20731",
               "sectionCode":"2263100000S74T20731",
               "name":"GESTIÓN DEL SERVICIO TI","modality":"R"}
            ]}
            """;

    private static final String GRAPHQL = """
            {"data":{"scheduleByDate":{"dates":[{"date":"2026-09-21","items":[
              {"name":"HERRAMIENTAS PARA LA COMUNICACIÓN EFECTIVA","period":"2263",
               "class":{"id":"gql-async","start":1790033400000,"end":1790038800000,
                 "descAmb":"AULA VIRTUAL","professors":{"firstName":"DOCENTE","lastName":"ASINCRONO"},
                 "location":{"classRoom":{"id":"AV55","floor":"V0"},"building":{"id":"B1","desc":"Pabellon"}},
                 "instructionMode":"P","linkZoom":null,
                 "linkCourseClass":"https://class.utp.edu.pe/student/courses/24577268-593a-5db7-a592-d676a656f171/section/83a80520-fc23-54ca-aa94-471582761354/learnv2"}},
              {"name":"GESTIÓN DEL SERVICIO TI","period":"2263",
               "class":{"id":"gql-live","start":1790033400000,"end":1790038400000,
                 "descAmb":"AULA VIRTUAL","professors":{"firstName":"MATIAS","lastName":"PALOMINO"},
                 "location":{"classRoom":{"id":"AV002","floor":"V0"},"building":{"id":"UTP_71","desc":"Pabellon"}},
                 "instructionMode":"R","linkZoom":"https://utpvirtual.zoom.us/j/88118281663",
                 "linkCourseClass":"https://class.utp.edu.pe/student/courses/c6341c15-436c-59af-8803-ce512fb45697/section/9fc26189-8030-5263-a048-96a2290effa4/learnv2"}}
            ]}]}}}
            """;

    private static UtpPortalGatewayAdapter adapterWithDashboard(int dashboardStatus, String dashboardBody,
                                                                 int graphqlStatus, String graphqlBody) {
        FakeHttpClient client = new FakeHttpClient(request -> {
            String uri = request.uri().toString();
            if (uri.contains("/dashboard-courses")) {
                return new FakeHttpClient.Response(dashboardStatus, dashboardBody);
            }
            if (uri.contains("/graphql")) {
                return new FakeHttpClient.Response(graphqlStatus, graphqlBody);
            }
            return new FakeHttpClient.Response(200,
                    "{\"data\":{\"current_interval\":{\"events\":[]}}}");
        });
        return new UtpPortalGatewayAdapter(client);
    }

    @Test
    @DisplayName("El horario se enriquece con courseCode, classNumber y modalidad del dashboard")
    void scheduleIsEnrichedWithCatalogCodeAndTypedModality() {
        ScheduleInterval interval = adapterWithDashboard(200, DASHBOARD, 200, GRAPHQL)
                .fetchSchedule(TOKEN, "2026 - Ciclo 2 Agosto");

        assertNotNull(interval.getClasses());

        ClassSession async = interval.getClasses().stream()
                .filter(c -> "100000S72V".equals(c.getCourseCode())).findFirst().orElseThrow();
        assertEquals("54262", async.getSection());
        assertEquals(ASYNC_SECTION, async.getSectionId());
        assertEquals(ModalityType.ASYNC_VIRTUAL, async.getModalityType());
        assertTrue(async.isAsyncVirtual());
        assertNull(async.getStartAt(), "VT no debe emitir franja fija que genere cruces falsos");
        assertNull(async.getFinishAt());
        assertEquals("TUTORÍA VIRTUAL 24/7", async.getEnvironmentType());

        ClassSession live = interval.getClasses().stream()
                .filter(c -> "100000S74T".equals(c.getCourseCode())).findFirst().orElseThrow();
        assertEquals("20731", live.getSection());
        assertEquals(LIVE_SECTION, live.getSectionId());
        assertEquals(ModalityType.REMOTE_LIVE, live.getModalityType());
        assertTrue(live.isLiveSynchronous());
        assertNotNull(live.getStartAt());
        assertEquals("https://utpvirtual.zoom.us/j/88118281663", live.getZoomLink());

        assertTrue(interval.getClasses().stream().noneMatch(c ->
                c.getCourseCode() == null || c.getCourseCode().isBlank() || c.getCourseCode().contains(" ")),
                "Ningún courseCode debe contener el título ni espacios");
        assertTrue(interval.getCourses().stream().anyMatch(c ->
                "100000S72V".equals(c.getCode()) && c.isAsyncVirtual()));
    }

    @Test
    @DisplayName("Un 5xx del dashboard se propaga y NO se degrada a horario vacío")
    void dashboardFailureFailsFastInsteadOfEmptySchedule() {
        UtpPortalGatewayAdapter adapter = adapterWithDashboard(502, "{}", 200,
                "{\"data\":{\"scheduleByDate\":{\"dates\":[]}}}");

        assertThrows(UpstreamServiceException.class,
                () -> adapter.fetchSchedule(TOKEN, "2026 - Ciclo 2 Agosto"));
    }

    @Test
    @DisplayName("La modalidad institucional se normaliza desde los valores reales de UTP")
    void modalityNormalization() {
        assertEquals(ModalityType.PRESENCIAL, ModalityType.from("P"));
        assertEquals(ModalityType.REMOTE_LIVE, ModalityType.from("R"));
        assertEquals(ModalityType.ASYNC_VIRTUAL, ModalityType.from("VT"));
        assertEquals(ModalityType.ASYNC_VIRTUAL, ModalityType.from("Virtual 24/7"));
        assertEquals(ModalityType.UNKNOWN, ModalityType.from(null));
    }
}
