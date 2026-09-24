package com.utp.horario.domain.port.out;

import com.utp.horario.domain.model.ScheduleInterval;

import java.util.Optional;

public interface ScheduleRepositoryPort {
    ScheduleInterval save(String studentId, ScheduleInterval schedule);
    Optional<ScheduleInterval> findByStudentIdAndPeriod(String studentId, String period);
}
