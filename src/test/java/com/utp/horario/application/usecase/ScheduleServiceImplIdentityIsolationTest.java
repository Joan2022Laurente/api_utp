package com.utp.horario.application.usecase;

import com.utp.horario.application.service.export.IcsCalendarExporter;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.port.out.ScheduleRepositoryPort;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import com.utp.horario.infrastructure.persistence.adapter.ScheduleRepositoryAdapter;
import com.utp.horario.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Pruebas de regresión del aislamiento de identidad en la caché de horarios.
 *
 * <p>Reproducen el hallazgo crítico C-02: la caché en memoria se indexaba con la clave
 * constante {@code "current-student"}, por lo que el horario del último estudiante
 * sincronizado era servido a cualquier llamador, incluidos anónimos y otros alumnos.</p>
 */
class ScheduleServiceImplIdentityIsolationTest {

    private static final String PERIOD = "2026 - Ciclo 2 Agosto";

    private ScheduleRepositoryPort repository;
    private UtpPortalGatewayPort gateway;
    private ScheduleServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = new ScheduleRepositoryAdapter();
        gateway = Mockito.mock(UtpPortalGatewayPort.class);
        service = new ScheduleServiceImpl(repository, gateway, new IcsCalendarExporter());

        when(gateway.fetchSchedule(any(), any()))
                .thenReturn(intervalFor("HORARIO-DE-UTP"));
    }

    @Test
    @DisplayName("El horario sincronizado por un alumno no se sirve a una petición anónima")
    void anonymousRequestDoesNotReceiveAnotherStudentsSchedule() {
        String tokenAlumnoA = TestTokens.validFor("U99999999");

        service.syncScheduleFromUtp(tokenAlumnoA, PERIOD);

        // El caché quedó indexado bajo el código del alumno A, no bajo una clave compartida.
        assertTrue(repository.findByStudentIdAndPeriod("U99999999", PERIOD).isPresent(),
                "El horario debe cachearse bajo el código del propio alumno");
        assertTrue(repository.findByStudentIdAndPeriod("current-student", PERIOD).isEmpty(),
                "No debe existir una entrada compartida 'current-student'");

        // Petición anónima: no debe devolver el horario del alumno A.
        ScheduleInterval anonimo = service.getStudentSchedule("current-student", PERIOD, null);
        assertNotNull(anonimo);
        assertTrue(anonimo.getClasses() == null || anonimo.getClasses().isEmpty(),
                "Una petición anónima no debe recibir el horario de otro estudiante");
    }

    @Test
    @DisplayName("Dos alumnos distintos no comparten el horario cacheado")
    void twoStudentsDoNotShareCachedSchedule() {
        String tokenA = TestTokens.validFor("U99999999");
        String tokenB = TestTokens.validFor("U21200000");

        service.syncScheduleFromUtp(tokenA, PERIOD);
        service.syncScheduleFromUtp(tokenB, PERIOD);

        assertTrue(repository.findByStudentIdAndPeriod("U99999999", PERIOD).isPresent());
        assertTrue(repository.findByStudentIdAndPeriod("U21200000", PERIOD).isPresent());
        assertTrue(repository.findByStudentIdAndPeriod("current-student", PERIOD).isEmpty(),
                "No debe existir una entrada compartida entre estudiantes");
    }

    @Test
    @DisplayName("La lectura con token propio se persiste bajo la identidad del solicitante")
    void readWithOwnTokenIsPersistedUnderOwnIdentity() {
        String token = TestTokens.validFor("U99999999");

        service.getStudentSchedule("current-student", PERIOD, token);

        assertTrue(repository.findByStudentIdAndPeriod("U99999999", PERIOD).isPresent(),
                "El horario debe indexarse por el código del alumno autenticado");
        assertTrue(repository.findByStudentIdAndPeriod("current-student", PERIOD).isEmpty());
    }

    private ScheduleInterval intervalFor(String id) {
        return ScheduleInterval.builder()
                .id(id)
                .periodName(PERIOD)
                .weekNumber(1)
                .totalWeeks(18)
                .courses(List.of())
                .classes(List.of())
                .build();
    }
}
