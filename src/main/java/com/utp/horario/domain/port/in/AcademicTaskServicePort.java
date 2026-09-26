package com.utp.horario.domain.port.in;

import com.utp.horario.domain.model.AcademicActivity;
import com.utp.horario.domain.model.CourseSummaryData;
import com.utp.horario.domain.model.TaskSpecification;

import java.util.List;

public interface AcademicTaskServicePort {
    TaskSpecification getTaskDetail(String sectionId, String activityId, String token);
    List<AcademicActivity> getCalendarActivities(String dateToQuery, String intervalMode, String token);
    List<AcademicActivity> getCalendarActivitiesFiltered(String dateToQuery, String intervalMode, String token, Integer week, String status, Boolean onlyGraded, String type);
    List<AcademicActivity> getUpcomingEvaluations(String token, int limit);
    CourseSummaryData getCourseSummary(String periodId, String token);
    com.utp.horario.domain.model.GradeSimulationResult simulateCourseGrade(String courseCode, double targetGrade, String periodId, String token);
    List<com.utp.horario.domain.model.GradeSimulationResult> simulateAllCourses(double targetGrade, String periodId, String token);
}
