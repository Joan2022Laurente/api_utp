package com.utp.horario.application.usecase;

import com.utp.horario.application.service.export.IcsCalendarExporter;
import com.utp.horario.application.service.security.SessionTokenInspector;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.port.in.ScheduleServicePort;
import com.utp.horario.domain.port.out.ScheduleRepositoryPort;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleServicePort {

    private final ScheduleRepositoryPort scheduleRepositoryPort;
    private final UtpPortalGatewayPort utpPortalGatewayPort;
    private final IcsCalendarExporter icsCalendarExporter;

    @Override
    public ScheduleInterval getStudentSchedule(String studentId, String period) {
        return getStudentSchedule(studentId, period, null);
    }

    /**
     * Obtiene el horario del estudiante autenticado.
     *
     * <p><b>Aislamiento de identidad:</b> la caché de horarios en memoria está indexada por
     * el código de alumno <b>derivado del token presentado</b>. Anteriormente se usaba la
     * clave constante {@code "current-student"}, por lo que el horario del último estudiante
     * sincronizado era servido a cualquier llamador (incluidos anónimos). Una petición sin
     * token nunca lee ni escribe la caché de ningún estudiante.</p>
     */
    @Override
    public ScheduleInterval getStudentSchedule(String studentId, String period, String token) {
        String sessionKey = resolveSessionKey(token);

        if (token != null && !token.isBlank()) {
            ScheduleInterval interval = utpPortalGatewayPort.fetchSchedule(token, period);
            if (!sessionKey.isBlank()) {
                return scheduleRepositoryPort.save(sessionKey, interval);
            }
            return interval;
        }

        // Petición anónima: se consulta el portal sin credenciales y se devuelve un intervalo
        // vacío; no se expone la caché de ningún otro estudiante.
        return utpPortalGatewayPort.fetchSchedule(token, period);
    }

    /**
     * Fuerza la sincronización del horario con el portal oficial UTP.
     */
    @Override
    public ScheduleInterval syncScheduleFromUtp(String token, String period) {
        String sessionKey = resolveSessionKey(token);
        ScheduleInterval interval = utpPortalGatewayPort.fetchSchedule(token, period);
        if (!sessionKey.isBlank()) {
            return scheduleRepositoryPort.save(sessionKey, interval);
        }
        return interval;
    }

    @Override
    public String exportScheduleToIcs(String studentId, String period, String token) {
        ScheduleInterval interval = getStudentSchedule(studentId, period, token);
        return icsCalendarExporter.exportSchedule(interval);
    }

    /**
     * Deriva la clave de sesión (código de alumno) a partir del token presentado.
     *
     * @return código de alumno en mayúsculas, o cadena vacía si no hay token válido.
     */
    private String resolveSessionKey(String token) {
        return SessionTokenInspector.tryInspect(token)
                .map(SessionTokenInspector.SessionClaims::studentCode)
                .filter(code -> code != null && !code.isBlank())
                .orElse("");
    }
}
