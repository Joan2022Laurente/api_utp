package com.utp.horario.domain.port.out;

import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.model.StudentProfile;
public interface UtpPortalGatewayPort {
    StudentProfile login(String username, String password);
    ScheduleInterval fetchSchedule(String token, String period);
    String fetchSyllabusPdfText(String token, String courseCode);
}
