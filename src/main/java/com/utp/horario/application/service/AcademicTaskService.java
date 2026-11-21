package com.utp.horario.application.service;

import com.utp.horario.domain.exception.ResourceNotFoundException;
import com.utp.horario.domain.exception.UpstreamServiceException;
import com.utp.horario.domain.model.AcademicActivity;
import com.utp.horario.domain.model.CompletenessStatus;
import com.utp.horario.domain.model.ContextProvenance;
import com.utp.horario.domain.model.CourseSummaryData;
import com.utp.horario.domain.model.MaterialTextExtraction;
import com.utp.horario.domain.model.SectionCurriculumTree;
import com.utp.horario.domain.model.SectionMaterial;
import com.utp.horario.domain.model.SectionTopic;
import com.utp.horario.domain.model.SectionUnit;
import com.utp.horario.domain.model.SectionWeek;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.model.SyllabusCorrelation;
import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import com.utp.horario.domain.model.TaskSpecification;
import com.utp.horario.domain.model.WeekAcademicContext;
import com.utp.horario.domain.model.WeekSyllabusContext;
import com.utp.horario.domain.port.in.AcademicTaskServicePort;
import com.utp.horario.domain.port.out.SyllabusRepositoryPort;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.utp.horario.application.service.formula.GradeSimulatorEngine;
import com.utp.horario.domain.model.GradeSimulationResult;

import java.net.URI;
import java.time.Duration;

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
    public com.fasterxml.jackson.databind.JsonNode getSectionFull(String sectionId, String token) {
        return utpPortalGatewayPort.fetchSectionFull(sectionId, token);
    }

    @Override
    public SectionCurriculumTree getSectionCurriculum(String sectionId, String token) {
        return utpPortalGatewayPort.fetchSectionCurriculum(sectionId, token);
    }

    @Override
    public WeekAcademicContext getWeekContext(String sectionId, int weekNumber, String token) {
        if (sectionId == null || sectionId.isBlank()) {
            throw new IllegalArgumentException("Se requiere sectionId para consultar el contexto de la semana.");
        }
        if (weekNumber < 1 || weekNumber > 30) {
            throw new IllegalArgumentException("Número de semana inválido: " + weekNumber);
        }

        List<CompletenessStatus.GapInfo> gaps = new ArrayList<>();

        // 1. Obtener árbol didáctico
        SectionCurriculumTree curriculum = null;
        try {
            curriculum = utpPortalGatewayPort.fetchSectionCurriculum(sectionId, token);
        } catch (Exception e) {
            gaps.add(CompletenessStatus.GapInfo.builder()
                    .source("SECTION_CURRICULUM")
                    .ref(sectionId)
                    .reason(e.getMessage())
                    .build());
        }

        String courseCode = (curriculum != null && curriculum.getCourseCode() != null)
                ? curriculum.getCourseCode()
                : utpPortalGatewayPort.resolveCourseCode(sectionId, null, token);

        // 2. Extraer temas del aula para la semana requerida
        List<SectionTopic> classroomTopics = new ArrayList<>();
        if (curriculum != null && curriculum.getUnits() != null) {
            for (SectionUnit u : curriculum.getUnits()) {
                if (u.getWeeks() != null) {
                    for (SectionWeek w : u.getWeeks()) {
                        if (w.getWeekNumber() != null && w.getWeekNumber() == weekNumber) {
                            if (w.getTopics() != null) {
                                classroomTopics.addAll(w.getTopics());
                            }
                        }
                    }
                }
            }
        }

        // 3. Obtener Sílabo oficial una sola vez (cero N+1)
        WeekSyllabusContext syllabusCtx = null;
        if (courseCode != null && !courseCode.isBlank() && syllabusRepositoryPort != null) {
            try {
                Optional<Syllabus> syllabusOpt = syllabusRepositoryPort.findByCourseCode(courseCode);
                if (syllabusOpt.isPresent()) {
                    Syllabus syl = syllabusOpt.get();
                    SyllabusWeeklySession session = null;
                    if (syl.getWeeklySchedule() != null) {
                        session = syl.getWeeklySchedule().stream()
                                .filter(s -> s != null && s.getWeek() != null && s.getWeek() == weekNumber)
                                .findFirst()
                                .orElse(null);
                    }

                    List<SyllabusEvaluation> evals = new ArrayList<>();
                    if (syl.getEvaluations() != null) {
                        evals = syl.getEvaluations().stream()
                                .filter(ev -> ev != null && ev.getWeek() != null && ev.getWeek() == weekNumber)
                                .collect(Collectors.toList());
                    }

                    syllabusCtx = WeekSyllabusContext.builder()
                            .weekNumber(weekNumber)
                            .unitTitle(session != null ? session.getUnit() : null)
                            .topic(session != null ? session.getTopic() : null)
                            .activities(session != null ? session.getActivities() : null)
                            .learningGoal(syl.getLearningGoal())
                            .scheduledEvaluations(evals)
                            .build();
                }
            } catch (Exception e) {
                gaps.add(CompletenessStatus.GapInfo.builder()
                        .source("SYLLABUS")
                        .ref(courseCode)
                        .reason(e.getMessage())
                        .build());
            }
        }

        // 4. Obtener tareas y actividades para la semana
        List<TaskSpecification> weekTasks = new ArrayList<>();
        try {
            List<AcademicActivity> activities = getCalendarActivitiesFiltered(null, "period", token, weekNumber, null, null, null);
            for (AcademicActivity act : activities) {
                if (act.getId() != null && !act.getId().isBlank()) {
                    try {
                        TaskSpecification taskSpec = getTaskDetail(sectionId, act.getId(), token);
                        if (taskSpec != null) {
                            weekTasks.add(taskSpec);
                        }
                    } catch (Exception e) {
                        gaps.add(CompletenessStatus.GapInfo.builder()
                                .source("HOMEWORK_RESUME")
                                .ref(act.getId())
                                .reason(e.getMessage())
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            gaps.add(CompletenessStatus.GapInfo.builder()
                    .source("CALENDAR_ACTIVITIES")
                    .ref("week-" + weekNumber)
                    .reason(e.getMessage())
                    .build());
        }

        boolean rubricAvailable = weekTasks.stream()
                .anyMatch(t -> Boolean.TRUE.equals(t.getRubricAvailable()));

        CompletenessStatus completeness = CompletenessStatus.builder()
                .status(gaps.isEmpty() ? "COMPLETE" : "PARTIAL")
                .gaps(gaps)
                .build();

        ContextProvenance provenance = ContextProvenance.builder()
                .syllabusSource("PDF_RECTOR_UTP")
                .classroomSource("CANVAS_PAO_CLASS")
                .fetchedAt(LocalDateTime.now().toString())
                .build();

        return WeekAcademicContext.builder()
                .weekNumber(weekNumber)
                .sectionId(sectionId)
                .courseCode(courseCode)
                .syllabus(syllabusCtx)
                .classroomTopics(classroomTopics)
                .tasks(weekTasks)
                .rubricAvailable(rubricAvailable)
                .completeness(completeness)
                .provenance(provenance)
                .build();
    }

    @Override
    public MaterialTextExtraction extractMaterialText(String sectionId, String materialId, String pages, int maxChars, String token) {
        if (sectionId == null || sectionId.isBlank()) {
            throw new IllegalArgumentException("Se requiere sectionId para extraer texto del material.");
        }
        if (materialId == null || materialId.isBlank()) {
            throw new IllegalArgumentException("Se requiere materialId para extraer texto del material.");
        }

        // 1. Obtener árbol para encontrar el material y validar pertenencia
        SectionCurriculumTree curriculum = utpPortalGatewayPort.fetchSectionCurriculum(sectionId, token);
        SectionMaterial matchedMaterial = null;
        if (curriculum != null && curriculum.getUnits() != null) {
            for (SectionUnit u : curriculum.getUnits()) {
                if (u.getWeeks() != null) {
                    for (SectionWeek w : u.getWeeks()) {
                        if (w.getTopics() != null) {
                            for (SectionTopic t : w.getTopics()) {
                                if (t.getMaterials() != null) {
                                    for (SectionMaterial m : t.getMaterials()) {
                                        if (materialId.equalsIgnoreCase(m.getId())) {
                                            matchedMaterial = m;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (matchedMaterial == null) {
            throw new ResourceNotFoundException("Material con ID " + materialId + " no encontrado en la sección " + sectionId);
        }

        String fileUrl = matchedMaterial.getUrl();
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("El material " + materialId + " no posee una URL de descarga asociada.");
        }

        // Anti-SSRF: solo permitir dominios autorizados de S3 / CloudFront de UTP
        try {
            URI uri = URI.create(fileUrl);
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            if (!host.endsWith(".amazonaws.com") && !host.endsWith(".cloudfront.net") && !host.endsWith("utp.edu.pe")) {
                throw new SecurityException("Host no autorizado para descarga de materiales: " + host);
            }
        } catch (IllegalArgumentException e) {
            throw new SecurityException("URL de material inválida: " + fileUrl);
        }

        // Descarga del documento
        int maxCharLimit = (maxChars > 0 && maxChars <= 50000) ? maxChars : 8000;
        List<Integer> extractedPages = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();
        String quality = "HIGH";

        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(fileUrl))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();

            java.net.http.HttpResponse<byte[]> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new UpstreamServiceException("Fallo al descargar material desde storage (HTTP " + resp.statusCode() + ").");
            }

            byte[] pdfBytes = resp.body();
            // Parsear con PDFBox
            try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdfBytes)) {
                int totalPages = document.getNumberOfPages();
                int startPage = 1;
                int endPage = Math.min(10, totalPages);

                if (pages != null && !pages.isBlank()) {
                    String[] parts = pages.split("-");
                    try {
                        startPage = Math.max(1, Integer.parseInt(parts[0].trim()));
                        if (parts.length > 1) {
                            endPage = Math.min(totalPages, Integer.parseInt(parts[1].trim()));
                        } else {
                            endPage = startPage;
                        }
                    } catch (NumberFormatException ignored) {}
                }

                org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                for (int p = startPage; p <= endPage; p++) {
                    stripper.setStartPage(p);
                    stripper.setEndPage(p);
                    String pageText = stripper.getText(document).trim();
                    if (!pageText.isEmpty()) {
                        extractedPages.add(p);
                        textBuilder.append("### Página ").append(p).append("\n\n").append(pageText).append("\n\n");
                    }
                    if (textBuilder.length() >= maxCharLimit) {
                        break;
                    }
                }
            }

            if (textBuilder.length() > maxCharLimit) {
                textBuilder.setLength(maxCharLimit);
                textBuilder.append("\n\n... [Contenido truncado por límite de caracteres]");
            }

            if (extractedPages.isEmpty()) {
                quality = "NO_TEXT";
            } else if (textBuilder.length() < 100) {
                quality = "LOW";
            }

        } catch (SecurityException | ResourceNotFoundException | UpstreamServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new UpstreamServiceException("Error durante la extracción de texto del material " + materialId + ": " + e.getMessage(), e);
        }

        return MaterialTextExtraction.builder()
                .materialId(materialId)
                .title(matchedMaterial.getTitle())
                .url(fileUrl)
                .pagesExtracted(extractedPages)
                .totalLength(textBuilder.length())
                .contentMarkdown(textBuilder.toString())
                .quality(quality)
                .build();
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
