package com.utp.horario.application.usecase;

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

    @Override
    public ScheduleInterval getStudentSchedule(String studentId, String period) {
        return getStudentSchedule(studentId, period, null);
    }

    @Override
    public ScheduleInterval getStudentSchedule(String studentId, String period, String token) {
        if (token != null && !token.isBlank()) {
            ScheduleInterval interval = utpPortalGatewayPort.fetchSchedule(token, period);
            return scheduleRepositoryPort.save(studentId, interval);
        }
        return scheduleRepositoryPort.findByStudentIdAndPeriod(studentId, period)
                .orElseGet(() -> syncScheduleFromUtp(token, period));
    }

    @Override
    public ScheduleInterval syncScheduleFromUtp(String token, String period) {
        ScheduleInterval interval = utpPortalGatewayPort.fetchSchedule(token, period);
        return scheduleRepositoryPort.save("current-student", interval);
    }
}
