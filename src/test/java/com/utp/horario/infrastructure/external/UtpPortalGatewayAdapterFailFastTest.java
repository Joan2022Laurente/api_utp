package com.utp.horario.infrastructure.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.exception.AuthenticationException;
import com.utp.horario.domain.exception.UpstreamServiceException;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.support.FakeHttpClient;
import com.utp.horario.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtpPortalGatewayAdapterFailFastTest {

    private static final String VALID_TOKEN = TestTokens.validFor("U21200000");

    private static UtpPortalGatewayAdapter adapterWithAcademicPeriods(int status, String body) {
        FakeHttpClient client = new FakeHttpClient(request -> {
            String uri = request.uri().toString();
            if (uri.contains("/academicperiods")) {
                return new FakeHttpClient.Response(status, body);
            }
            return new FakeHttpClient.Response(200, "{}");
        });
        return new UtpPortalGatewayAdapter(client);
    }

    @Test
    @DisplayName("resolveCurrentAcademicPeriod sin token lanza AuthenticationException")
    void blankTokenThrowsAuthenticationException() {
        UtpPortalGatewayAdapter adapter = adapterWithAcademicPeriods(200, "[]");

        assertThrows(AuthenticationException.class, () -> adapter.resolveCurrentAcademicPeriod(""));
        assertThrows(AuthenticationException.class, () -> adapter.resolveCurrentAcademicPeriod(null));
        assertThrows(AuthenticationException.class, () -> adapter.resolveCurrentAcademicPeriod("   "));
    }

    @Test
    @DisplayName("resolveCurrentAcademicPeriod ante 401 de PAO lanza AuthenticationException")
    void upstream401ThrowsAuthenticationException() {
        UtpPortalGatewayAdapter adapter = adapterWithAcademicPeriods(401, "{\"message\":\"unauthorized\"}");

        assertThrows(AuthenticationException.class, () -> adapter.resolveCurrentAcademicPeriod(VALID_TOKEN));
    }

    @Test
    @DisplayName("resolveCurrentAcademicPeriod ante 403 de PAO lanza AuthenticationException")
    void upstream403ThrowsAuthenticationException() {
        UtpPortalGatewayAdapter adapter = adapterWithAcademicPeriods(403, "{\"message\":\"forbidden\"}");

        assertThrows(AuthenticationException.class, () -> adapter.resolveCurrentAcademicPeriod(VALID_TOKEN));
    }

    @Test
    @DisplayName("resolveCurrentAcademicPeriod ante 502 de PAO lanza UpstreamServiceException")
    void upstream502ThrowsUpstreamServiceException() {
        UtpPortalGatewayAdapter adapter = adapterWithAcademicPeriods(502, "Bad Gateway");

        assertThrows(UpstreamServiceException.class, () -> adapter.resolveCurrentAcademicPeriod(VALID_TOKEN));
    }

    @Test
    @DisplayName("resolveCurrentAcademicPeriod si ningún periodo tiene isCurrent=true selecciona el periodo más reciente por cronograma")
    void noCurrentPeriodSelectsLatestPeriod() {
        String json = """
                [
                  {"id":"2253","code":"2025-2","name":"2025 - Ciclo 2 Agosto","isCurrent":false,"startDate":"2025-08-15","endDate":"2025-12-15"},
                  {"id":"2261","code":"2026-1","name":"2026 - Ciclo 1 Marzo","isCurrent":false,"startDate":"2026-03-15","endDate":"2026-07-15"}
                ]
                """;
        UtpPortalGatewayAdapter adapter = adapterWithAcademicPeriods(200, json);

        UtpPortalGatewayAdapter.AcademicPeriodInfo info = adapter.resolveCurrentAcademicPeriod(VALID_TOKEN);
        assertNotNull(info);
        assertEquals("2261", info.id());
        assertEquals("2026-1", info.code());
    }

    @Test
    @DisplayName("resolveCurrentAcademicPeriod si el catálogo está vacío lanza UpstreamServiceException")
    void emptyPeriodsCatalogThrowsUpstreamServiceException() {
        UtpPortalGatewayAdapter adapter = adapterWithAcademicPeriods(200, "[]");

        UpstreamServiceException ex = assertThrows(UpstreamServiceException.class,
                () -> adapter.resolveCurrentAcademicPeriod(VALID_TOKEN));
        assertTrue(ex.getMessage().contains("no reportó periodos"));
    }

    @Test
    @DisplayName("resolveAcademicPeriod con nombre parcial (ej. sin sufijo PREG) resuelve el periodo institucional flexiblemente")
    void resolveAcademicPeriodFlexibleNameMatchingSuccess() {
        String json = """
                [
                  {"id":"2263","code":"2026-2","name":"2026 - Ciclo 2 Agosto PREG","isCurrent":false,"startDate":"2026-08-15","endDate":"2026-12-13"}
                ]
                """;
        UtpPortalGatewayAdapter adapter = adapterWithAcademicPeriods(200, json);

        UtpPortalGatewayAdapter.AcademicPeriodInfo info = adapter.resolveAcademicPeriod(VALID_TOKEN, "2026 - Ciclo 2 Agosto");
        assertNotNull(info);
        assertEquals("2263", info.id());
        assertEquals("2026 - Ciclo 2 Agosto PREG", info.name());
    }

    @Test
    @DisplayName("resolveAcademicPeriod con periodo específico existente lo resuelve exitosamente")
    void resolveSpecificExistingPeriodSuccess() {
        String json = """
                [
                  {"id":"2253","code":"2025-2","name":"2025 - Ciclo 2 Agosto","isCurrent":false,"startDate":"2025-08-10","endDate":"2025-12-10"},
                  {"id":"2263","code":"2026-2","name":"2026 - Ciclo 2 Agosto","isCurrent":true,"startDate":"2026-08-15","endDate":"2026-12-13"}
                ]
                """;
        UtpPortalGatewayAdapter adapter = adapterWithAcademicPeriods(200, json);

        UtpPortalGatewayAdapter.AcademicPeriodInfo info = adapter.resolveAcademicPeriod(VALID_TOKEN, "2253");
        assertNotNull(info);
        assertEquals("2253", info.id());
        assertEquals("2025 - Ciclo 2 Agosto", info.name());
        assertFalse(info.isCurrent());
        assertEquals(LocalDate.of(2025, 8, 10), info.startDate());
    }

    @Test
    @DisplayName("resolveAcademicPeriod con periodo inexistente lanza UpstreamServiceException")
    void resolveSpecificNonExistentPeriodThrows() {
        String json = """
                [
                  {"id":"2263","code":"2026-2","name":"2026 - Ciclo 2 Agosto","isCurrent":true,"startDate":"2026-08-15","endDate":"2026-12-13"}
                ]
                """;
        UtpPortalGatewayAdapter adapter = adapterWithAcademicPeriods(200, json);

        UpstreamServiceException ex = assertThrows(UpstreamServiceException.class,
                () -> adapter.resolveAcademicPeriod(VALID_TOKEN, "9999"));
        assertTrue(ex.getMessage().contains("no fue reportado"));
    }

    @Test
    @DisplayName("AcademicPeriodInfo con fechas nulas lanza IllegalStateException al pedir totalWeeks")
    void academicPeriodInfoWithNullDatesThrowsIllegalStateException() {
        UtpPortalGatewayAdapter.AcademicPeriodInfo info = new UtpPortalGatewayAdapter.AcademicPeriodInfo(
                "2263", "2026-2", "2026 - Ciclo 2", true, null, null);

        assertThrows(IllegalStateException.class, info::totalWeeks);
    }

    @Test
    @DisplayName("AcademicPeriodInfo calcula semanas dinámicamente según fechas reales")
    void academicPeriodInfoCalculatesTotalWeeksDynamically() {
        UtpPortalGatewayAdapter.AcademicPeriodInfo info16 = new UtpPortalGatewayAdapter.AcademicPeriodInfo(
                "2263", "2026-2", "2026 - Ciclo 2", true,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 12, 1));

        assertTrue(info16.totalWeeks() > 0);
        assertEquals(16, info16.totalWeeks());
    }

    @Test
    @DisplayName("fetchSyllabusPdfText con URL vacía o nula retorna null")
    void fetchSyllabusPdfTextEmptyUrlReturnsNull() {
        UtpPortalGatewayAdapter adapter = adapterWithAcademicPeriods(200, "[]");

        assertNull(adapter.fetchSyllabusPdfText(VALID_TOKEN, null));
        assertNull(adapter.fetchSyllabusPdfText(VALID_TOKEN, ""));
        assertNull(adapter.fetchSyllabusPdfText(VALID_TOKEN, "   "));
    }

    @Test
    @DisplayName("resolveAcademicPeriod con payload envuelto en 'data' lo desenvuelve y resuelve exitosamente")
    void resolveAcademicPeriodWrappedInDataSuccess() {
        String json = """
                {
                  "data": [
                    {"id":"2263","code":"2026-2","name":"2026 - Ciclo 2 Agosto","isCurrent":true,"startDate":"2026-08-15","endDate":"2026-12-13"}
                  ]
                }
                """;
        UtpPortalGatewayAdapter adapter = adapterWithAcademicPeriods(200, json);

        UtpPortalGatewayAdapter.AcademicPeriodInfo info = adapter.resolveAcademicPeriod(VALID_TOKEN, "2026-2");
        assertNotNull(info);
        assertEquals("2263", info.id());
        assertEquals("2026 - Ciclo 2 Agosto", info.name());
        assertTrue(info.isCurrent());
    }

    @Test
    @DisplayName("resolveAcademicPeriod por código (ej. 2026-2) lo resuelve insensible a mayúsculas")
    void resolveAcademicPeriodByCodeSuccess() {
        String json = """
                [
                  {"id":"2263","code":"2026-2","name":"2026 - Ciclo 2 Agosto","isCurrent":true,"startDate":"2026-08-15","endDate":"2026-12-13"}
                ]
                """;
        UtpPortalGatewayAdapter adapter = adapterWithAcademicPeriods(200, json);

        UtpPortalGatewayAdapter.AcademicPeriodInfo info = adapter.resolveAcademicPeriod(VALID_TOKEN, "2026-2");
        assertNotNull(info);
        assertEquals("2263", info.id());
        assertEquals("2026 - Ciclo 2 Agosto", info.name());
    }

    @Test
    @DisplayName("resolveAcademicPeriod ante fecha ISO corrupta lanza UpstreamServiceException específica")
    void malformedIsoDateThrowsUpstreamServiceException() {
        String json = """
                [
                  {"id":"2263","code":"2026-2","name":"2026 - Ciclo 2","isCurrent":true,"startDate":"fecha-corrupta","endDate":"2026-12-13"}
                ]
                """;
        UtpPortalGatewayAdapter adapter = adapterWithAcademicPeriods(200, json);

        UpstreamServiceException ex = assertThrows(UpstreamServiceException.class,
                () -> adapter.resolveAcademicPeriod(VALID_TOKEN, null));
        assertTrue(ex.getMessage().contains("formato ISO inválido"));
    }

    @Test
    @DisplayName("fetchSchedule con periodo histórico resuelve semanas, fechas y nombre de ese periodo")
    void fetchScheduleWithHistoricPeriodResolvesHistoricDatesAndWeeks() {
        FakeHttpClient client = new FakeHttpClient(request -> {
            String uri = request.uri().toString();
            if (uri.contains("/academicperiods")) {
                return new FakeHttpClient.Response(200, """
                        [
                          {"id":"2253","code":"2025-2","name":"2025 - Ciclo 2 Agosto","isCurrent":false,"startDate":"2025-08-10","endDate":"2025-11-30"},
                          {"id":"2263","code":"2026-2","name":"2026 - Ciclo 2 Agosto","isCurrent":true,"startDate":"2026-08-15","endDate":"2026-12-13"}
                        ]
                        """);
            }
            if (uri.contains("/dashboard-courses")) {
                return new FakeHttpClient.Response(200, "{\"data\":[]}");
            }
            if (uri.contains("/graphql")) {
                return new FakeHttpClient.Response(200, "{\"data\":{\"scheduleByDate\":{\"dates\":[]}}}");
            }
            return new FakeHttpClient.Response(200, "{}");
        });
        UtpPortalGatewayAdapter adapter = new UtpPortalGatewayAdapter(client);

        ScheduleInterval interval = adapter.fetchSchedule(VALID_TOKEN, "2253");
        assertNotNull(interval);
        assertEquals("2025 - Ciclo 2 Agosto", interval.getPeriodName());
        assertEquals(LocalDate.of(2025, 8, 10), interval.getStartDate());
        assertEquals(LocalDate.of(2025, 11, 30), interval.getEndDate());
        assertTrue(interval.getTotalWeeks() > 0);
        assertEquals(17, interval.getTotalWeeks());
    }

    @Test
    @DisplayName("fetchSchedule con periodo inválido que no existe en institución falla de inmediato")
    void fetchScheduleWithInvalidPeriodThrowsUpstreamServiceException() {
        FakeHttpClient client = new FakeHttpClient(request -> {
            String uri = request.uri().toString();
            if (uri.contains("/academicperiods")) {
                return new FakeHttpClient.Response(200, """
                        [{"id":"2263","code":"2026-2","name":"2026 - Ciclo 2 Agosto","isCurrent":true,"startDate":"2026-08-15","endDate":"2026-12-13"}]
                        """);
            }
            return new FakeHttpClient.Response(200, "{}");
        });
        UtpPortalGatewayAdapter adapter = new UtpPortalGatewayAdapter(client);

        assertThrows(UpstreamServiceException.class, () -> adapter.fetchSchedule(VALID_TOKEN, "periodo-inexistente"));
    }
}
