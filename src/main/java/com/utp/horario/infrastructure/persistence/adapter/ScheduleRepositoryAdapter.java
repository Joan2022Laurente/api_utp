package com.utp.horario.infrastructure.persistence.adapter;

import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.port.out.ScheduleRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ScheduleRepositoryAdapter implements ScheduleRepositoryPort {

    private final ConcurrentHashMap<String, ScheduleInterval> cache = new ConcurrentHashMap<>();

    @Override
    public ScheduleInterval save(String studentId, ScheduleInterval schedule) {
        String key = studentId + ":" + (schedule.getPeriodName() != null ? schedule.getPeriodName() : "default");
        cache.put(key, schedule);
        return schedule;
    }

    @Override
    public Optional<ScheduleInterval> findByStudentIdAndPeriod(String studentId, String period) {
        String key = studentId + ":" + (period != null ? period : "default");
        return Optional.ofNullable(cache.get(key));
    }
}
