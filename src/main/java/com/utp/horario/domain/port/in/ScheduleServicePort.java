package com.utp.horario.domain.port.in;

import com.utp.horario.domain.model.ScheduleInterval;

public interface ScheduleServicePort {
    ScheduleInterval getStudentSchedule(String studentId, String period);
    ScheduleInterval getStudentSchedule(String studentId, String period, String token);
    ScheduleInterval syncScheduleFromUtp(String token, String period);
}
