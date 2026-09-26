package com.utp.horario.application.service;

import com.utp.horario.domain.model.AcademicActivity;
import com.utp.horario.domain.model.CourseSummaryData;
import com.utp.horario.domain.model.TaskSpecification;
import com.utp.horario.domain.port.in.AcademicTaskServicePort;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.utp.horario.application.service.formula.GradeSimulatorEngine;
import com.utp.horario.domain.model.GradeSimulationResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicTaskService implements AcademicTaskServicePort {

    private final UtpPortalGatewayPort utpPortalGatewayPort;
    private final GradeSimulatorEngine gradeSimulatorEngine;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern EVALUATION_TITLE_PATTERN = Pattern.compile("(?i)\\b(AP[1-3]|APF[1-3]|PC[1-4]|EXFN|PROY|PTF|PA|ATI[1-3]|TI|TA[1-4]|LC[1-4]|PRÁCTICA|PORTAFOLIO|EXAMEN|PROYECTO)\\b");

    @Override
    public TaskSpecification getTaskDetail(String sectionId, String activityId, String token) {
        return utpPortalGatewayPort.fetchTaskSpecification(sectionId, activityId, token);
    }

    @Override
    public List<AcademicActivity> getCalendarActivities(String dateToQuery, String intervalMode, String token) {
        return getCalendarActivitiesFiltered(dateToQuery, intervalMode, token, null, null, null, null);
    }

    @Override
    public List<AcademicActivity> getCalendarActivitiesFiltered(String dateToQuery, String intervalMode, String token, Integer week, String status, Boolean onlyGraded, String type) {
        List<AcademicActivity> activities = utpPortalGatewayPort.fetchCalendarActivities(dateToQuery, intervalMode, token);
        if (activities == null || activities.isEmpty()) {
            return new ArrayList<>();
        }

        return activities.stream()
                .filter(a -> week == null || (a.getWeekNumber() != null && a.getWeekNumber().equals(week)))
                .filter(a -> status == null || status.isBlank() || (a.getStudentStatus() != null && a.getStudentStatus().equalsIgnoreCase(status.trim())))
                .filter(a -> onlyGraded == null || !onlyGraded || "WEIGHTED_EVALUATION".equals(a.getClassificationCategory()) || Boolean.TRUE.equals(a.getIsQualified()))
                .filter(a -> type == null || type.isBlank() || (a.getActivityType() != null && a.getActivityType().equalsIgnoreCase(type.trim())))
                .collect(Collectors.toList());
    }

    @Override
    public List<AcademicActivity> getUpcomingEvaluations(String token, int limit) {
        List<AcademicActivity> all = utpPortalGatewayPort.fetchCalendarActivities(null, "period", token);
        if (all == null || all.isEmpty()) {
            return new ArrayList<>();
        }

        LocalDateTime now = LocalDateTime.now();

        return all.stream()
                .filter(a -> a.getFinishAt() != null && !a.getFinishAt().isBlank())
                .filter(a -> {
                    boolean hasEvalSystem = a.getEvaluationSystem() != null && !a.getEvaluationSystem().isBlank();
                    boolean hasEvalTitle = a.getTitle() != null && EVALUATION_TITLE_PATTERN.matcher(a.getTitle()).find();
                    boolean isQualified = Boolean.TRUE.equals(a.getIsQualified());
                    return hasEvalSystem || hasEvalTitle || isQualified;
                })
                .filter(a -> {
                    LocalDateTime finish = parseDateTimeSafe(a.getFinishAt());
                    return finish.isAfter(now.minusDays(1)); // Mostrar tareas del día actual o futuro
                })
                .collect(Collectors.toMap(
                        a -> (a.getId() != null && !a.getId().isBlank()) ? a.getId() : (a.getTitle() + a.getFinishAt()),
                        a -> a,
                        (existing, replacement) -> existing,
                        java.util.LinkedHashMap::new
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(a -> parseDateTimeSafe(a.getFinishAt())))
                .limit(limit > 0 ? limit : 10)
                .collect(Collectors.toList());
    }

    @Override
    public CourseSummaryData getCourseSummary(String periodId, String token) {
        return utpPortalGatewayPort.fetchCourseSummary(periodId, token);
    }

    @Override
    public GradeSimulationResult simulateCourseGrade(String courseCode, double targetGrade, String periodId, String token) {
        CourseSummaryData summary = getCourseSummary(periodId, token);
        if (summary == null || summary.getCourses() == null || summary.getCourses().isEmpty()) {
            throw new IllegalStateException("No se encontraron cursos matriculados en el periodo " + periodId);
        }

        String search = courseCode != null ? courseCode.trim().toUpperCase() : "";
        CourseSummaryData.CourseGradeItem matched = summary.getCourses().stream()
                .filter(c -> (c.getCourseCode() != null && c.getCourseCode().equalsIgnoreCase(search))
                        || (c.getCourseId() != null && c.getCourseId().equalsIgnoreCase(search))
                        || (c.getCourseName() != null && c.getCourseName().toUpperCase().contains(search)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No se encontró ningún curso con código o denominación: " + courseCode));

        return gradeSimulatorEngine.simulate(matched, targetGrade);
    }

    @Override
    public List<GradeSimulationResult> simulateAllCourses(double targetGrade, String periodId, String token) {
        CourseSummaryData summary = getCourseSummary(periodId, token);
        if (summary == null || summary.getCourses() == null || summary.getCourses().isEmpty()) {
            return List.of();
        }

        return summary.getCourses().stream()
                .map(course -> gradeSimulatorEngine.simulate(course, targetGrade))
                .collect(Collectors.toList());
    }

    private LocalDateTime parseDateTimeSafe(String dtStr) {
        try {
            return LocalDateTime.parse(dtStr.trim(), DATE_TIME_FORMATTER);
        } catch (Exception e) {
            return LocalDateTime.MAX;
        }
    }
}
