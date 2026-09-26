package com.utp.horario.domain.port.out;

import com.utp.horario.domain.model.AcademicActivity;
import com.utp.horario.domain.model.CourseSummaryData;
import com.utp.horario.domain.model.ScheduleInterval;
import com.utp.horario.domain.model.StudentProfile;
import com.utp.horario.domain.model.TaskSpecification;

import java.util.List;

public interface UtpPortalGatewayPort {
    StudentProfile login(String username, String password);
    StudentProfile refreshToken(String refreshToken);
    ScheduleInterval fetchSchedule(String token, String period);
    String fetchSyllabusPdfText(String token, String courseCode);
    TaskSpecification fetchTaskSpecification(String sectionId, String activityId, String token);
    List<AcademicActivity> fetchCalendarActivities(String dateToQuery, String intervalMode, String token);
    CourseSummaryData fetchCourseSummary(String periodId, String token);
}

