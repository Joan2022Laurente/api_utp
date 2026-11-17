package com.utp.horario.infrastructure.external;

import com.utp.horario.domain.exception.AuthenticationException;
import com.utp.horario.domain.exception.ResourceNotFoundException;
import com.utp.horario.domain.exception.UpstreamServiceException;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.support.FakeHttpClient;
import com.utp.horario.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del mapeo de errores HTTP del portal externo UTP.
 *
 * <p>Garantizan que un fallo upstream nunca se traduzca en una respuesta exitosa vacia:
 * 401/403 se convierten en {@link AuthenticationException}, 404 en
 * {@link ResourceNotFoundException} y 5xx (o un 200 con {@code errors} de GraphQL) en
 * {@link UpstreamServiceException}.</p>
 */
class UtpPortalGatewayAdapterUpstreamErrorTest {

    private static final String TOKEN = TestTokens.validFor("U12345678");

    @Test
    @DisplayName("El calendario de actividades propaga 401 como fallo de autenticación")
    void calendarActivitiesMaps401ToAuthenticationException() {
        UtpPortalGatewayAdapter adapter = adapterAlways(401, "{\"message\":\"unauthorized\"}");

        assertThrows(AuthenticationException.class,
                () -> adapter.fetchCalendarActivities("2026-09-21", "period", TOKEN));
    }

    @Test
    @DisplayName("El calendario de actividades propaga 404 como recurso inexistente")
    void calendarActivitiesMaps404ToResourceNotFound() {
        UtpPortalGatewayAdapter adapter = adapterAlways(404, "{}");

        assertThrows(ResourceNotFoundException.class,
                () -> adapter.fetchCalendarActivities("2026-09-21", "period", TOKEN));
    }

    @Test
    @DisplayName("El calendario de actividades propaga 502 como fallo upstream")
    void calendarActivitiesMaps502ToUpstreamFailure() {
        UtpPortalGatewayAdapter adapter = adapterAlways(502, "{}");

        assertThrows(UpstreamServiceException.class,
                () -> adapter.fetchCalendarActivities("2026-09-21", "period", TOKEN));
    }

    @Test
    @DisplayName("Un 200 con errors de GraphQL no se acepta como calendario vacío")
    void calendarActivitiesRejectsGraphQlErrors() {
        UtpPortalGatewayAdapter adapter = adapterAlways(200,
                "{\"errors\":[{\"message\":\"internal\"}],\"data\":null}");

        assertThrows(UpstreamServiceException.class,
                () -> adapter.fetchCalendarActivities("2026-09-21", "period", TOKEN));
    }

    @Test
    @DisplayName("El resumen de cursos propaga 401 como fallo de autenticación")
    void courseSummaryMaps401ToAuthenticationException() {
        UtpPortalGatewayAdapter adapter = adapterAlways(401, "{}");

        assertThrows(AuthenticationException.class,
                () -> adapter.fetchCourseSummary("2263", TOKEN));
    }

    @Test
    @DisplayName("El resumen de cursos propaga 404 como recurso inexistente")
    void courseSummaryMaps404ToResourceNotFound() {
        UtpPortalGatewayAdapter adapter = adapterAlways(404, "{}");

        assertThrows(ResourceNotFoundException.class,
                () -> adapter.fetchCourseSummary("2263", TOKEN));
    }

    @Test
    @DisplayName("Un 200 con errors de GraphQL no se acepta como resumen vacío")
    void courseSummaryRejectsGraphQlErrors() {
        UtpPortalGatewayAdapter adapter = adapterAlways(200,
                "{\"errors\":[{\"message\":\"internal\"}],\"data\":null}");

        assertThrows(UpstreamServiceException.class,
                () -> adapter.fetchCourseSummary("2263", TOKEN));
    }

    @Test
    @DisplayName("Un 200 sin summary se reporta como fallo upstream, no como resumen vacío")
    void courseSummaryRejectsMissingSummary() {
        UtpPortalGatewayAdapter adapter = adapterAlways(200,
                "{\"data\":{\"getCourseSummary\":{\"courses\":[]}}}");

        assertThrows(UpstreamServiceException.class,
                () -> adapter.fetchCourseSummary("2263", TOKEN));
    }

    @Test
    @DisplayName("La tarea propaga 404 como recurso inexistente")
    void taskSpecificationMaps404ToResourceNotFound() {
        UtpPortalGatewayAdapter adapter = adapterWithRoutes(request -> {
            String uri = request.uri().toString();
            if (uri.contains("/dashboard-courses")) {
                return new FakeHttpClient.Response(200, "{\"data\":[]}");
            }
            return new FakeHttpClient.Response(404, "{}");
        });

        assertThrows(ResourceNotFoundException.class,
                () -> adapter.fetchTaskSpecification("12345", "hw-1", TOKEN));
    }

    @Test
    @DisplayName("La tarea rechaza un 200 con errors de PAO")
    void taskSpecificationRejectsGraphQlErrors() {
        UtpPortalGatewayAdapter adapter = adapterWithRoutes(request -> {
            String uri = request.uri().toString();
            if (uri.contains("/dashboard-courses")) {
                return new FakeHttpClient.Response(200, "{\"data\":[]}");
            }
            return new FakeHttpClient.Response(200, "{\"errors\":[{\"message\":\"internal\"}]}");
        });

        assertThrows(UpstreamServiceException.class,
                () -> adapter.fetchTaskSpecification("12345", "hw-1", TOKEN));
    }

    @Test
    @DisplayName("La tarea rechaza un 200 sin título en lugar de inventar 'Asignación'")
    void taskSpecificationRejectsMissingTitle() {
        UtpPortalGatewayAdapter adapter = adapterWithRoutes(request -> {
            String uri = request.uri().toString();
            if (uri.contains("/dashboard-courses")) {
                return new FakeHttpClient.Response(200, "{\"data\":[]}");
            }
            return new FakeHttpClient.Response(200, "{\"data\":{\"id\":\"hw-1\",\"content\":\"\"}}");
        });

        assertThrows(UpstreamServiceException.class,
                () -> adapter.fetchTaskSpecification("12345", "hw-1", TOKEN));
    }

    @Test
    @DisplayName("Un 401 del Portal GraphQL aborta el horario de inmediato y no fabrica un 200 vacío")
    void scheduleGraphQl401FailsFast() {
        FakeHttpClient client = FakeHttpClient.always(401, "{}");
        UtpPortalGatewayAdapter adapter = new UtpPortalGatewayAdapter(client);

        assertThrows(AuthenticationException.class,
                () -> adapter.fetchSchedule(TOKEN, "2026 - Ciclo 2 Agosto"));
        assertTrue(client.requests().size() <= 2,
                "Un 401 debe abortar sin recorrer las 18 semanas (peticiones: " + client.requests().size() + ")");
    }

    @Test
    @DisplayName("Si GraphQL y PAO fallan, el horario se reporta como error upstream")
    void scheduleWithAllUpstreamFailuresIsNotAnEmptySuccess() {
        UtpPortalGatewayAdapter adapter = adapterWithRoutes(request -> {
            String uri = request.uri().toString();
            if (uri.contains("/graphql")) {
                return new FakeHttpClient.Response(502, "{}");
            }
            return new FakeHttpClient.Response(502, "{}");
        });

        assertThrows(UpstreamServiceException.class,
                () -> adapter.fetchSchedule(TOKEN, "2026 - Ciclo 2 Agosto"));
    }

    @Test
    @DisplayName("Un 200 genuino sin eventos sí se acepta como periodo vacío")
    void scheduleWithGenuineEmptyResponseReturnsEmptyInterval() {
        UtpPortalGatewayAdapter adapter = adapterWithRoutes(request -> {
            String uri = request.uri().toString();
            if (uri.contains("/graphql")) {
                return new FakeHttpClient.Response(200,
                        "{\"data\":{\"scheduleByDate\":{\"dates\":[]}}}");
            }
            return new FakeHttpClient.Response(200,
                    "{\"data\":{\"current_interval\":{\"period_name\":\"2026 - Ciclo 2 Agosto\","
                            + "\"week_number\":1,\"total_weeks\":18,\"events\":[]}}}");
        });

        ScheduleInterval interval = adapter.fetchSchedule(TOKEN, "2026 - Ciclo 2 Agosto");

        assertNotNull(interval);
        assertNotNull(interval.getClasses());
        assertTrue(interval.getClasses().isEmpty());
        assertFalse(interval.getCourses() == null);
        assertEquals("2026 - Ciclo 2 Agosto", interval.getPeriodName());
    }

    private static UtpPortalGatewayAdapter adapterAlways(int status, String body) {
        return new UtpPortalGatewayAdapter(FakeHttpClient.always(status, body));
    }

    private static UtpPortalGatewayAdapter adapterWithRoutes(FakeHttpClient.Handler handler) {
        return new UtpPortalGatewayAdapter(new FakeHttpClient(handler));
    }
}
