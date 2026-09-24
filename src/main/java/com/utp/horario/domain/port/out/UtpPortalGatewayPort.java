package com.utp.horario.domain.port.out;

import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.model.StudentProfile;
import com.utp.horario.domain.model.TaskSyncItem;

import java.util.List;

public interface UtpPortalGatewayPort {
    StudentProfile login(String username, String password);
    ScheduleInterval fetchSchedule(String token, String period);
    List<TaskSyncItem> fetchTasks(String token, String sectionId);
    String fetchSyllabusPdfText(String token, String courseCode);
}
