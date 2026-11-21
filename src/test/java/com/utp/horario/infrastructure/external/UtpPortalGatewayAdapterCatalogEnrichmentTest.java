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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                 "instructionMode":"VT","classType":"3","linkZoom":null,
                 "linkCourseClass":"https://class.utp.edu.pe/student/courses/24577268-593a-5db7-a592-d676a656f171/section/83a80520-fc23-54ca-aa94-471582761354/learnv2"}},
              {"name":"GESTIÓN DEL SERVICIO TI","period":"2263",
               "class":{"id":"gql-live","start":1790033400000,"end":1790038400000,
                 "descAmb":"AULA VIRTUAL","professors":{"firstName":"MATIAS","lastName":"PALOMINO"},
                 "location":{"classRoom":{"id":"AV002","floor":"V0"},"building":{"id":"UTP_71","desc":"Pabellon"}},
                 "instructionMode":"R","linkZoom":"https://utpvirtual.zoom.us/j/88118281663",
                 "linkCourseClass":"https://class.utp.edu.pe/student/courses/c6341c15-436c-59af-8803-ce512fb45697/section/9fc26189-8030-5263-a048-96a2290effa4/learnv2"}}
            ]}]}}}
            """;

    private static final String FAKE_ACADEMIC_PERIODS =
            "[{\"id\":\"2263\",\"code\":\"2026-2\",\"name\":\"2026 - Ciclo 2 Agosto\",\"isCurrent\":true,\"startDate\":\"2026-08-15\",\"endDate\":\"2026-12-13\"}]";

    private static UtpPortalGatewayAdapter adapterWithDashboard(int dashboardStatus, String dashboardBody,
                                                                 int graphqlStatus, String graphqlBody) {
        FakeHttpClient client = new FakeHttpClient(request -> {
            String uri = request.uri().toString();
            if (uri.contains("/dashboard-courses")) {
                return new FakeHttpClient.Response(dashboardStatus, dashboardBody);
            }
            if (uri.contains("/academicperiods")) {
                return new FakeHttpClient.Response(200, FAKE_ACADEMIC_PERIODS);
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

        assertTrue(interval.getClasses().stream()
                        .noneMatch(c -> c.isAsyncVirtual() || "VT".equals(c.getModality())),
                "classes no debe contener ninguna sesión para cursos VT");
        assertTrue(interval.getCourses().stream()
                        .anyMatch(c -> c.isAsyncVirtual() && "100000S72V".equals(c.getCode())),
                "courses debe incluir la asignatura VT enriquecida");

        com.utp.horario.domain.model.Course vtCourse = interval.getCourses().stream()
                .filter(c -> "100000S72V".equals(c.getCode())).findFirst().orElseThrow();
        assertEquals("54262", vtCourse.getSection());
        assertEquals(ASYNC_SECTION, vtCourse.getSectionId());
        assertEquals(ModalityType.ASYNC_VIRTUAL, vtCourse.getModalityType());
        assertTrue(vtCourse.isAsyncVirtual());
        assertNotNull(vtCourse.getClassLink(), "El enlace Canvas debe conservarse aunque no haya sesiones");

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
    @DisplayName("Cursos de taller THP (10000TE103) e ítems malformados no causan NPE y enriquecen correctamente")
    void workshopCourseAndMalformedItemsDoNotCrashSchedule() {
        String dashboardWithWorkshopAndMalformed = """
                {"data":[
                  {"sectionId":"65d46c7a-fddc-5449-87d7-3102c748e034",
                   "courseCode":"10000TE103","classNumber":"25301",
                   "sectionCode":"999910000TE10325301","period":"9999",
                   "name":"Crea Apps Web: Node.js y Angular / THP","modality":"R"},
                  {"sectionId":"malformed-section-null-code",
                   "courseCode":null,"classNumber":null,
                   "sectionCode":"MALFORMED","period":"2263",
                   "name":"Curso Sin Codigo","modality":"P"},
                  {"sectionId":"old-period-section",
                   "courseCode":"100000HIST","classNumber":"11111",
                   "sectionCode":"2253100000HIST11111","period":"2253",
                   "name":"Curso Historico","modality":"P"},
                  {"sectionId":"9fc26189-8030-5263-a048-96a2290effa4",
                   "courseCode":"100000S74T","classNumber":"20731",
                   "sectionCode":"2263100000S74T20731","period":"2263",
                   "name":"GESTIÓN DEL SERVICIO TI","modality":"R"}
                ]}
                """;

        UtpPortalGatewayAdapter adapter = adapterWithDashboard(200, dashboardWithWorkshopAndMalformed, 200, GRAPHQL);
        ScheduleInterval interval = adapter.fetchSchedule(TOKEN, "2263");

        assertNotNull(interval);
        assertNotNull(interval.getClasses());
        assertFalse(interval.getClasses().isEmpty());

        ClassSession live = interval.getClasses().stream()
                .filter(c -> "100000S74T".equals(c.getCourseCode()))
                .findFirst().orElseThrow();
        assertEquals("20731", live.getSection());

        // Verificar que el código de taller THP se resuelve sin error desde la pasarela
        String resolvedWorkshop = adapter.resolveCourseCode("65d46c7a-fddc-5449-87d7-3102c748e034", "Crea Apps Web: Node.js y Angular / THP", TOKEN);
        assertEquals("10000TE103", resolvedWorkshop);
    }

    @Test
    @DisplayName("Fixture real de Estudiante B (57 cursos, talleres THP, historial): extrae los cursos activos de 2263 y enriquece sin colisiones")
    void studentBRealFixtureColdStartRegressionTest() throws Exception {
        java.nio.file.Path fixturePath = java.nio.file.Path.of("laboratorio/live_DASHBOARD_COURSES_U23238787.json");
        if (!java.nio.file.Files.exists(fixturePath)) {
            return;
        }
        String realJson = java.nio.file.Files.readString(fixturePath);

        // GraphQL simulando una clase de "GESTIÓN DEL SERVICIO TI" para la alumna B (U23238787)
        String graphqlStudentB = """
                {"data":{"scheduleByDate":{"dates":[{"date":"2026-09-21","items":[
                  {"name":"GESTIÓN DEL SERVICIO TI","period":"2263",
                   "class":{"id":"gql-s72t","start":1790033400000,"end":1790038400000,
                     "descAmb":"AULA VIRTUAL","professors":{"firstName":"DOCENTE","lastName":"TI"},
                     "location":{"classRoom":{"id":"AV002","floor":"V0"},"building":{"id":"UTP_71","desc":"Pabellon"}},
                     "instructionMode":"R","linkZoom":"https://utpvirtual.zoom.us/j/88118281663",
                     "linkCourseClass":"https://class.utp.edu.pe/student/courses/c6341c15-436c-59af-8803-ce512fb45697/section/2018260b-87d2-4363-a602-0da2a688aeb7/learnv2"}}
                ]}]}}}
                """;

        String tokenStudentB = TestTokens.validFor("U23238787");
        UtpPortalGatewayAdapter adapter = adapterWithDashboard(200, realJson, 200, graphqlStudentB);

        ScheduleInterval interval = adapter.fetchSchedule(tokenStudentB, "2263");
        assertNotNull(interval);
        assertNotNull(interval.getClasses());
        assertFalse(interval.getClasses().isEmpty());

        ClassSession session = interval.getClasses().get(0);
        // Para la estudiante B (malla ciclo 8), "GESTIÓN DEL SERVICIO TI" es 100000S72T, NO 100000S74T de Joan
        assertEquals("100000S72T", session.getCourseCode());
        assertEquals("PARTIAL", interval.getEnrichmentStatus(), "El edificio UTP_71 del fixture no resuelve contra getBuildings: enrichmentStatus debe ser PARTIAL");

        // Verificar resolución de taller THP desde la pasarela sin colisiones
        String workshopCode = adapter.resolveCourseCode("65d46c7a-fddc-5449-87d7-3102c748e034", "Crea Apps Web como un Pro: Domina Node.js y Angular / THP", tokenStudentB);
        assertEquals("10000TE103", workshopCode);
    }

    @Test
    @DisplayName("Aislamiento estricto multitenant: Estudiante A (Joan, S74T) y Estudiante B (S72T) sin sectionId fuerzan resolución por nombre y no se contaminan")
    void dualStudentCrossContaminationRegressionTest() {
        String tokenJoan = TestTokens.validFor("U23307609");
        String tokenStudentB = TestTokens.validFor("U23238787");

        // Los dashboards tienen nombres de curso idénticos pero mallas distintas (S74T vs S72T)
        String dashboardJoan = """
                {"data":[
                  {"sectionId":"sec-joan-s74t",
                   "courseCode":"100000S74T","classNumber":"20731",
                   "sectionCode":"2263100000S74T20731","period":"2263",
                   "name":"GESTIÓN DEL SERVICIO TI","modality":"R"}
                ]}
                """;

        String dashboardStudentB = """
                {"data":[
                  {"sectionId":"sec-b-s72t",
                   "courseCode":"100000S72T","classNumber":"20182",
                   "sectionCode":"2263100000S72T20182","period":"2263",
                   "name":"GESTIÓN DEL SERVICIO TI","modality":"R"}
                ]}
                """;

        // GraphQL SIN linkCourseClass ni sectionId emparejable:
        // OBLIGA AL SISTEMA a resolver el courseCode a través del nombre del curso
        // y del caché particionado por studentCode (prohibido caer en match por UUID).
        String graphqlJoan = """
                {"data":{"scheduleByDate":{"dates":[{"date":"2026-09-21","items":[
                  {"name":"GESTIÓN DEL SERVICIO TI","period":"2263",
                   "class":{"id":"gql-joan","start":1790033400000,"end":1790038400000,
                     "descAmb":"AULA VIRTUAL","professors":{"firstName":"DOCENTE","lastName":"JOAN"},
                     "location":{"classRoom":{"id":"AV001","floor":"V0"},"building":{"id":"UTP_71","desc":"Pabellon"}},
                     "instructionMode":"R","linkZoom":"https://utpvirtual.zoom.us/j/joan",
                     "linkCourseClass":null}}
                ]}]}}}
                """;

        String graphqlStudentB = """
                {"data":{"scheduleByDate":{"dates":[{"date":"2026-09-21","items":[
                  {"name":"GESTIÓN DEL SERVICIO TI","period":"2263",
                   "class":{"id":"gql-b","start":1790033400000,"end":1790038400000,
                     "descAmb":"AULA VIRTUAL","professors":{"firstName":"DOCENTE","lastName":"B"},
                     "location":{"classRoom":{"id":"AV002","floor":"V0"},"building":{"id":"UTP_71","desc":"Pabellon"}},
                     "instructionMode":"R","linkZoom":"https://utpvirtual.zoom.us/j/b",
                     "linkCourseClass":null}}
                ]}]}}}
                """;

        FakeHttpClient client = new FakeHttpClient(request -> {
            String uri = request.uri().toString();
            String auth = request.headers().firstValue("Authorization").orElse("");
            boolean isJoan = auth.contains(tokenJoan);
            if (uri.contains("/dashboard-courses")) {
                return new FakeHttpClient.Response(200, isJoan ? dashboardJoan : dashboardStudentB);
            }
            if (uri.contains("/academicperiods")) {
                return new FakeHttpClient.Response(200, FAKE_ACADEMIC_PERIODS);
            }
            if (uri.contains("/graphql")) {
                return new FakeHttpClient.Response(200, isJoan ? graphqlJoan : graphqlStudentB);
            }
            return new FakeHttpClient.Response(200, "{\"data\":{\"buildings\":[]}}");
        });

        UtpPortalGatewayAdapter adapter = new UtpPortalGatewayAdapter(client);

        // SECUENCIA 1: Joan (A) -> Estudiante B (B) -> Joan (A)
        ScheduleInterval schedJoan1 = adapter.fetchSchedule(tokenJoan, "2263");
        assertNotNull(schedJoan1);
        assertEquals("100000S74T", schedJoan1.getClasses().get(0).getCourseCode(),
                "Joan debe resolver su código de malla 100000S74T por nombre");

        ScheduleInterval schedB1 = adapter.fetchSchedule(tokenStudentB, "2263");
        assertNotNull(schedB1);
        assertEquals("100000S72T", schedB1.getClasses().get(0).getCourseCode(),
                "Estudiante B debe resolver su código de malla 100000S72T por nombre en la misma instancia");

        ScheduleInterval schedJoan2 = adapter.fetchSchedule(tokenJoan, "2263");
        assertNotNull(schedJoan2);
        assertEquals("100000S74T", schedJoan2.getClasses().get(0).getCourseCode(),
                "Joan no debe ser contaminado por la sincronización previa de Estudiante B");

        // SECUENCIA 2: Estudiante B (B) -> Joan (A) -> Estudiante B (B)
        ScheduleInterval schedB2 = adapter.fetchSchedule(tokenStudentB, "2263");
        assertNotNull(schedB2);
        assertEquals("100000S72T", schedB2.getClasses().get(0).getCourseCode(),
                "Estudiante B no debe ser contaminado por la sincronización de Joan");

        // Verificación directa de resolveCourseCode forzando sectionId NULL
        // para garantizar que la consulta golpee exclusivamente a courseNameToCourseCodeCache
        assertEquals("100000S74T", adapter.resolveCourseCode(null, "GESTIÓN DEL SERVICIO TI", tokenJoan),
                "resolveCourseCode por nombre puro debe retornar 100000S74T para Joan");
        assertEquals("100000S72T", adapter.resolveCourseCode(null, "GESTIÓN DEL SERVICIO TI", tokenStudentB),
                "resolveCourseCode por nombre puro debe retornar 100000S72T para Estudiante B");
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
