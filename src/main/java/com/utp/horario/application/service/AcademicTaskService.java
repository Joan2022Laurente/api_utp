package com.utp.horario.application.service;

import com.utp.horario.domain.model.AcademicActivity;
import com.utp.horario.domain.model.CourseSummaryData;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.model.SyllabusCorrelation;
import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import com.utp.horario.domain.model.TaskSpecification;
import com.utp.horario.domain.port.in.AcademicTaskServicePort;
import com.utp.horario.domain.port.out.SyllabusRepositoryPort;
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
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicTaskService implements AcademicTaskServicePort {

    private final UtpPortalGatewayPort utpPortalGatewayPort;
    private final GradeSimulatorEngine gradeSimulatorEngine;
    private final SyllabusRepositoryPort syllabusRepositoryPort;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern EVALUATION_TITLE_PATTERN = Pattern.compile("(?i)\\b(AP[1-3]|APF[1-3]|PC[1-4]|EXFN|PROY|PTF|PA|ATI[1-3]|TI|TA[1-4]|LC[1-4]|PRÁCTICA|PORTAFOLIO|EXAMEN|PROYECTO)\\b");

    @Override
    public TaskSpecification getTaskDetail(String sectionId, String activityId, String token) {
        TaskSpecification spec = utpPortalGatewayPort.fetchTaskSpecification(sectionId, activityId, token);
        if (spec != null) {
            SyllabusCorrelation correlation = correlateWithSyllabus(
                    spec.getCourseCode(),
                    spec.getEvaluationSystem(),
                    spec.getTitle(),
                    null
            );
            spec = spec.toBuilder().syllabusCorrelation(correlation).build();
        }
        return spec;
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
                .map(a -> {
                    SyllabusCorrelation correlation = correlateWithSyllabus(
                            a.getCourseCode(),
                            a.getEvaluationSystem(),
                            a.getTitle(),
                            a.getWeekNumber()
                    );
                    return a.toBuilder().syllabusCorrelation(correlation).build();
                })
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

    public SyllabusCorrelation correlateWithSyllabus(String courseCode, String evalSystem, String title, Integer weekNumber) {
        if (courseCode == null || courseCode.isBlank()) {
            return null;
        }

        SyllabusCorrelation.SyllabusCorrelationBuilder builder = SyllabusCorrelation.builder()
                .courseCode(courseCode)
                .syllabusUrl("/api/v1/syllabus/" + courseCode)
                .syllabusMarkdownUrl("/api/v1/syllabus/" + courseCode + "/markdown")
                .isSyllabusMatched(false);

        if (syllabusRepositoryPort == null) {
            return builder.build();
        }

        try {
            Optional<Syllabus> syllabusOpt = syllabusRepositoryPort.findByCourseCode(courseCode);
            if (syllabusOpt.isEmpty()) {
                return builder.build();
            }

            Syllabus syllabus = syllabusOpt.get();

            // 1. Buscar evaluación correspondiente en el sílabo
            SyllabusEvaluation matchedEval = findMatchingEvaluation(syllabus.getEvaluations(), evalSystem, title, weekNumber);

            // 2. Buscar tema / unidad de la semana académica
            SyllabusWeeklySession matchedWeek = null;
            if (weekNumber != null && syllabus.getWeeklySchedule() != null) {
                matchedWeek = syllabus.getWeeklySchedule().stream()
                        .filter(w -> w != null && w.getWeek() != null && w.getWeek().equals(weekNumber))
                        .findFirst()
                        .orElse(null);
            }

            if (matchedEval != null) {
                builder.isSyllabusMatched(true)
                        .evaluationType(matchedEval.getType())
                        .weightPercent(matchedEval.getWeightPercent())
                        .evaluationDescription(matchedEval.getDescription())
                        .syllabusWeek(matchedEval.getWeek() != null ? matchedEval.getWeek() : weekNumber);
            } else if (weekNumber != null) {
                builder.syllabusWeek(weekNumber);
            }

            if (matchedWeek != null) {
                builder.syllabusUnit(matchedWeek.getUnit());
                builder.syllabusTopic(matchedWeek.getTopic());
            }

            return builder.build();
        } catch (Exception e) {
            log.warn("[AcademicTaskService] Error al correlacionar sílabo para curso {}: {}", courseCode, e.getMessage());
            return builder.build();
        }
    }

    private SyllabusEvaluation findMatchingEvaluation(List<SyllabusEvaluation> evaluations, String evalSystem, String title, Integer weekNumber) {
        if (evaluations == null || evaluations.isEmpty()) return null;

        String cleanEval = evalSystem != null ? evalSystem.toUpperCase().trim() : "";
        String cleanTitle = title != null ? title.toUpperCase().trim() : "";

        // Intento 1: Match por descripción exacta o contenida (ej. "AVANCE DE PORTAFOLIO 2")
        if (!cleanEval.isEmpty()) {
            for (SyllabusEvaluation ev : evaluations) {
                if (ev.getDescription() != null && !ev.getDescription().isBlank()) {
                    String evDesc = ev.getDescription().toUpperCase().trim();
                    if (evDesc.equals(cleanEval) || cleanEval.contains(evDesc) || evDesc.contains(cleanEval)) {
                        return ev;
                    }
                }
            }
        }

        // Intento 2: Match por tipo mnemónico (ej: AP2, APF1, PC1, EF, PROY, ATI1) en título o sistema
        for (SyllabusEvaluation ev : evaluations) {
            if (ev.getType() != null && !ev.getType().isBlank()) {
                String type = ev.getType().toUpperCase().trim();
                Pattern p = Pattern.compile("(?i)\\b" + Pattern.quote(type) + "\\b");
                if (p.matcher(cleanEval).find() || p.matcher(cleanTitle).find()) {
                    return ev;
                }
            }
        }

        // Intento 3: Match por semana académica si coincide
        if (weekNumber != null && weekNumber > 0) {
            for (SyllabusEvaluation ev : evaluations) {
                if (ev.getWeek() != null && ev.getWeek().equals(weekNumber)) {
                    if (!cleanTitle.isEmpty() && ev.getType() != null && cleanTitle.contains(ev.getType())) {
                        return ev;
                    }
                }
            }
        }

        return null;
    }

    private LocalDateTime parseDateTimeSafe(String dtStr) {
        try {
            return LocalDateTime.parse(dtStr.trim(), DATE_TIME_FORMATTER);
        } catch (Exception e) {
            return LocalDateTime.MAX;
        }
    }
}
