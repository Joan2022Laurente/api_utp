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
import com.utp.horario.domain.port.in.SyllabusServicePort;
import com.utp.horario.domain.port.out.SyllabusRepositoryPort;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.utp.horario.application.service.formula.GradeSimulatorEngine;
import com.utp.horario.domain.model.GradeSimulationResult;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcademicTaskService implements AcademicTaskServicePort {

    private final UtpPortalGatewayPort utpPortalGatewayPort;
    private final GradeSimulatorEngine gradeSimulatorEngine;
    private final SyllabusRepositoryPort syllabusRepositoryPort;

    @Autowired(required = false)
    @Lazy
    private SyllabusServicePort syllabusServicePort;

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

        // 1. Obtener árbol didáctico (una sola llamada al gateway si está disponible)
        com.fasterxml.jackson.databind.JsonNode fullTree = null;
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

        String effectiveSectionId = (curriculum != null && curriculum.getSectionId() != null && !curriculum.getSectionId().isBlank())
                ? curriculum.getSectionId()
                : sectionId;

        String courseCode = (curriculum != null && curriculum.getCourseCode() != null)
                ? curriculum.getCourseCode()
                : utpPortalGatewayPort.resolveCourseCode(effectiveSectionId, null, token);

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

        // 3. Obtener Sílabo oficial mandatorio (una sola consulta)
        WeekSyllabusContext syllabusCtx = null;
        Optional<Syllabus> cachedSyllabusOpt = Optional.empty();
        if (courseCode != null && !courseCode.isBlank()) {
            try {
                if (syllabusRepositoryPort != null) {
                    cachedSyllabusOpt = syllabusRepositoryPort.findByCourseCode(courseCode);
                }
                if (cachedSyllabusOpt.isEmpty() && syllabusServicePort != null && token != null && !token.isBlank()) {
                    try {
                        Syllabus fetchedSyllabus = syllabusServicePort.getSyllabus(courseCode, effectiveSectionId, null, token);
                        if (fetchedSyllabus != null) {
                            cachedSyllabusOpt = Optional.of(fetchedSyllabus);
                        }
                    } catch (Exception sylEx) {
                        gaps.add(CompletenessStatus.GapInfo.builder()
                                .source("SYLLABUS")
                                .ref(courseCode)
                                .reason(sylEx.getMessage())
                                .build());
                    }
                }
                if (cachedSyllabusOpt.isPresent()) {
                    Syllabus syl = cachedSyllabusOpt.get();
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

        // 4. Obtener tareas SOLO de esta sección (árbol de sección + calendario + actividades pendientes activas en paralelo)
        Map<String, TaskSpecification> weekTasksById = new LinkedHashMap<>();
        Set<String> taskActivityIds = new LinkedHashSet<>();
        Map<String, Integer> activityWeekMap = new LinkedHashMap<>();

        java.util.concurrent.CompletableFuture<com.fasterxml.jackson.databind.JsonNode> treeFuture =
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return utpPortalGatewayPort.fetchSectionFull(effectiveSectionId, token);
                    } catch (Exception e) {
                        return null;
                    }
                });
        java.util.concurrent.CompletableFuture<List<AcademicActivity>> calFuture =
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return utpPortalGatewayPort.fetchCalendarActivities(null, "period", token);
                    } catch (Exception e) {
                        synchronized (gaps) {
                            gaps.add(CompletenessStatus.GapInfo.builder()
                                    .source("CALENDAR_ACTIVITIES")
                                    .ref("week-" + weekNumber)
                                    .reason(e.getMessage())
                                    .build());
                        }
                        return List.of();
                    }
                });
        java.util.concurrent.CompletableFuture<List<AcademicActivity>> pendingFuture =
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return utpPortalGatewayPort.fetchPendingActivitiesResume(token);
                    } catch (Exception e) {
                        synchronized (gaps) {
                            gaps.add(CompletenessStatus.GapInfo.builder()
                                    .source("PENDING_RESUME")
                                    .ref(effectiveSectionId)
                                    .reason(e.getMessage())
                                    .build());
                        }
                        return List.of();
                    }
                });

        fullTree = treeFuture.join();
        collectWeekHomeworkIdsFromTree(fullTree, weekNumber, taskActivityIds);
        for (String id : taskActivityIds) {
            activityWeekMap.putIfAbsent(id, weekNumber);
        }

        List<AcademicActivity> rawCalActivities = calFuture.join();
        if (rawCalActivities != null) {
            for (AcademicActivity act : rawCalActivities) {
                if (act.getWeekNumber() == null || act.getWeekNumber() != weekNumber) {
                    continue;
                }
                if (!matchesSectionOrCourse(act, effectiveSectionId, courseCode)) {
                    continue;
                }
                String resolvedActId = (act.getActivityId() != null && !act.getActivityId().isBlank())
                        ? act.getActivityId()
                        : act.getId();
                if (resolvedActId != null && !resolvedActId.isBlank()) {
                    taskActivityIds.add(resolvedActId);
                    activityWeekMap.putIfAbsent(resolvedActId, act.getWeekNumber());
                }
            }
        }

        // Incluir tareas activas de pending/resume de esta misma sección (incluso si el docente las dejó en otra semana con fecha extendida)
        List<AcademicActivity> pendingActivities = pendingFuture.join();
        if (pendingActivities != null) {
            for (AcademicActivity act : pendingActivities) {
                if (!matchesSectionOrCourse(act, effectiveSectionId, courseCode)) {
                    continue;
                }
                String actType = act.getActivityType() != null ? act.getActivityType().trim().toUpperCase(Locale.ROOT) : "";
                if (!"HOMEWORK".equals(actType) && !"EVALUATION".equals(actType)) {
                    continue;
                }
                String resolvedActId = (act.getActivityId() != null && !act.getActivityId().isBlank())
                        ? act.getActivityId()
                        : act.getId();
                if (resolvedActId != null && !resolvedActId.isBlank()) {
                    taskActivityIds.add(resolvedActId);
                    if (act.getWeekNumber() != null && act.getWeekNumber() > 0) {
                        activityWeekMap.putIfAbsent(resolvedActId, act.getWeekNumber());
                    }
                }
            }
        }

        final Optional<Syllabus> finalSylOpt = cachedSyllabusOpt;
        List<java.util.concurrent.CompletableFuture<TaskSpecification>> taskFutures = taskActivityIds.stream()
                .map(actId -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        TaskSpecification spec = utpPortalGatewayPort.fetchTaskSpecification(effectiveSectionId, actId, token);
                        if (spec != null) {
                            Integer resolvedWeek = activityWeekMap.getOrDefault(actId, weekNumber);
                            SyllabusCorrelation corr = correlateWithLoadedSyllabus(
                                    finalSylOpt, spec.getCourseCode(), spec.getEvaluationSystem(), spec.getTitle(), resolvedWeek);
                            return spec.toBuilder()
                                    .weekNumber(resolvedWeek)
                                    .syllabusCorrelation(corr)
                                    .build();
                        }
                        return null;
                    } catch (Exception e) {
                        synchronized (gaps) {
                            gaps.add(CompletenessStatus.GapInfo.builder()
                                    .source("HOMEWORK_RESUME")
                                    .ref(actId)
                                    .reason(e.getMessage())
                                    .build());
                        }
                        return null;
                    }
                }))
                .toList();

        for (var f : taskFutures) {
            TaskSpecification taskSpec = f.join();
            if (taskSpec != null) {
                String key = (taskSpec.getId() != null && !taskSpec.getId().isBlank()) ? taskSpec.getId() : taskSpec.getTitle();
                weekTasksById.putIfAbsent(key, taskSpec);
            }
        }

        List<TaskSpecification> weekTasks = new ArrayList<>(weekTasksById.values());
        sortWeekTasksActiveFirst(weekTasks);

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
                .sectionId(effectiveSectionId)
                .courseCode(courseCode)
                .syllabus(syllabusCtx)
                .classroomTopics(classroomTopics)
                .tasks(weekTasks)
                .rubricAvailable(rubricAvailable)
                .completeness(completeness)
                .provenance(provenance)
                .build();
    }

    private void sortWeekTasksActiveFirst(List<TaskSpecification> tasks) {
        if (tasks == null || tasks.size() <= 1) return;
        LocalDateTime nowLima = LocalDateTime.now(java.time.ZoneId.of("America/Lima"));
        tasks.sort((a, b) -> {
            boolean aActive = isTaskActive(a, nowLima);
            boolean bActive = isTaskActive(b, nowLima);
            if (aActive != bActive) {
                return aActive ? -1 : 1;
            }
            LocalDateTime aDue = parseTaskDateTime(a);
            LocalDateTime bDue = parseTaskDateTime(b);
            if (aDue != null && bDue != null) {
                return aActive ? aDue.compareTo(bDue) : bDue.compareTo(aDue);
            }
            if (aDue != null) return -1;
            if (bDue != null) return 1;
            return 0;
        });
    }

    private boolean isTaskActive(TaskSpecification task, LocalDateTime nowLima) {
        if (task == null) return false;
        String status = task.getHomeworkStatus() != null
                ? task.getHomeworkStatus().trim().toUpperCase(Locale.ROOT)
                : "";
        if ("OUT_OF_DATE".equals(status) || "EXPIRED".equals(status) || "CLOSED".equals(status)) {
            return false;
        }
        LocalDateTime due = parseTaskDateTime(task);
        if (due != null && due.isBefore(nowLima)) {
            return false;
        }
        return true;
    }

    private LocalDateTime parseTaskDateTime(TaskSpecification task) {
        String raw = task.getDueAt();
        if (raw == null || raw.isBlank()) raw = task.getAvailableUntil();
        if (raw == null || raw.isBlank()) raw = task.getLockAt();
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.trim().replace('T', ' ');
        if (cleaned.length() > 19) {
            cleaned = cleaned.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(cleaned, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(cleaned.substring(0, Math.min(16, cleaned.length())),
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } catch (Exception ignored2) {
                return null;
            }
        }
    }

    private SyllabusCorrelation correlateWithLoadedSyllabus(
            Optional<Syllabus> syllabusOpt, String courseCode, String evalSystem, String title, Integer weekNumber) {
        if (courseCode == null || courseCode.isBlank()) {
            return null;
        }
        if (syllabusOpt == null || syllabusOpt.isEmpty()) {
            return SyllabusCorrelation.builder()
                    .courseCode(courseCode)
                    .syllabusUrl("/api/v1/syllabus/" + courseCode)
                    .syllabusMarkdownUrl("/api/v1/syllabus/" + courseCode + "/markdown")
                    .isSyllabusMatched(false)
                    .build();
        }
        Syllabus syllabus = syllabusOpt.get();
        SyllabusCorrelation.SyllabusCorrelationBuilder builder = SyllabusCorrelation.builder()
                .courseCode(courseCode)
                .syllabusUrl("/api/v1/syllabus/" + courseCode)
                .syllabusMarkdownUrl("/api/v1/syllabus/" + courseCode + "/markdown")
                .isSyllabusMatched(false);
        SyllabusEvaluation matchedEval = findMatchingEvaluation(syllabus.getEvaluations(), evalSystem, title, weekNumber);
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
    }

    private boolean matchesSectionOrCourse(AcademicActivity act, String effectiveSectionId, String courseCode) {
        if (act == null) return false;
        if (act.getSectionId() != null && !act.getSectionId().isBlank()) {
            return act.getSectionId().trim().equalsIgnoreCase(effectiveSectionId.trim());
        }
        return courseCode != null && !courseCode.isBlank()
                && act.getCourseCode() != null
                && act.getCourseCode().trim().equalsIgnoreCase(courseCode.trim());
    }

    private void collectWeekHomeworkIdsFromTree(com.fasterxml.jackson.databind.JsonNode root, int weekNumber, Set<String> targetIds) {
        if (root == null || root.isNull()) return;
        com.fasterxml.jackson.databind.JsonNode unities = root.has("unities") ? root.path("unities") : root.path("units");
        if (!unities.isArray()) return;
        for (com.fasterxml.jackson.databind.JsonNode u : unities) {
            com.fasterxml.jackson.databind.JsonNode themes = u.has("themes") ? u.path("themes") : u.path("weeks");
            if (!themes.isArray()) continue;
            for (com.fasterxml.jackson.databind.JsonNode theme : themes) {
                int wk = theme.hasNonNull("weekNumber") ? theme.path("weekNumber").asInt(-1) : theme.path("number").asInt(-1);
                if (wk != weekNumber) continue;
                com.fasterxml.jackson.databind.JsonNode contents = theme.has("contents") ? theme.path("contents") : theme.path("topics");
                if (!contents.isArray()) continue;
                for (com.fasterxml.jackson.databind.JsonNode c : contents) {
                    String type = c.path("type").asText("").trim().toUpperCase(Locale.ROOT);
                    if ("HOMEWORK".equals(type) || "EVALUATION".equals(type)) {
                        String actId = c.path("metadata").path("activityId").asText("");
                        if (actId.isBlank()) actId = c.path("activityId").asText("");
                        if (actId.isBlank()) actId = c.path("contentId").asText("");
                        if (actId.isBlank()) actId = c.path("id").asText("");
                        if (!actId.isBlank()) {
                            targetIds.add(actId);
                        }
                    }
                }
            }
        }
    }

    @Override
    public MaterialTextExtraction extractMaterialText(String sectionId, String materialId, String pages, int maxChars, String token) {
        if (sectionId == null || sectionId.isBlank()) {
            throw new IllegalArgumentException("Se requiere sectionId para extraer texto del material.");
        }
        if (materialId == null || materialId.isBlank()) {
            throw new IllegalArgumentException("Se requiere materialId para extraer texto del material.");
        }

        // 1. Obtener árbol para encontrar el material y validar pertenencia (ID exacto o título)
        SectionCurriculumTree curriculum = utpPortalGatewayPort.fetchSectionCurriculum(sectionId, token);
        String effectiveSectionId = (curriculum != null && curriculum.getSectionId() != null && !curriculum.getSectionId().isBlank())
                ? curriculum.getSectionId()
                : sectionId;
        SectionMaterial matchedMaterial = findMaterialInTree(curriculum, materialId);

        // 1b. Si no estuvo en los materiales directos, buscar en los adjuntos de las tareas de la sección
        if (matchedMaterial == null) {
            matchedMaterial = findMaterialInHomeworkAttachments(effectiveSectionId, materialId, token);
        }

        if (matchedMaterial == null) {
            throw new ResourceNotFoundException("Material con ID '" + materialId + "' no encontrado en la sección " + effectiveSectionId);
        }

        String fileUrl = matchedMaterial.getUrl();
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("El material " + materialId + " no posee una URL de descarga asociada.");
        }

        // Enlaces externos de video/web (no binarios S3) se reportan limpiamente sin intentar descargar HTML como documento
        String matType = matchedMaterial.getType() != null ? matchedMaterial.getType().toUpperCase(Locale.ROOT) : "";
        String lowerUrl = fileUrl.toLowerCase(Locale.ROOT);
        if (("URL".equals(matType) || "LINK".equals(matType))
                && !lowerUrl.endsWith(".pdf") && !lowerUrl.endsWith(".docx") && !lowerUrl.endsWith(".pptx")
                && !lowerUrl.endsWith(".txt") && !lowerUrl.endsWith(".md")) {
            return MaterialTextExtraction.builder()
                    .materialId(matchedMaterial.getId() != null ? matchedMaterial.getId() : materialId)
                    .title(matchedMaterial.getTitle())
                    .url(fileUrl)
                    .pagesExtracted(List.of())
                    .totalLength(0)
                    .contentMarkdown("Enlace externo de referencia (" + matType + "): " + fileUrl)
                    .quality("LINK_ONLY")
                    .build();
        }

        URI parsedUri = validateExternalMaterialUrl(fileUrl);

        // Descarga y extracción multi-formato (PDF, DOCX, PPTX, TXT/MD)
        int maxCharLimit = (maxChars > 0 && maxChars <= 50000) ? maxChars : 12000;
        List<Integer> extractedPages = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();
        String quality = "HIGH";

        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(parsedUri)
                    .timeout(Duration.ofSeconds(25))
                    .GET()
                    .build();

            java.net.http.HttpResponse<byte[]> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new UpstreamServiceException("Fallo al descargar material desde storage (HTTP " + resp.statusCode() + ").");
            }

            byte[] fileBytes = resp.body();
            String contentType = resp.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);

            if (isPdfResource(fileBytes, lowerUrl, contentType)) {
                extractPdfText(fileBytes, pages, maxCharLimit, extractedPages, textBuilder);
            } else if (isDocxResource(lowerUrl, contentType, matType)) {
                extractDocxText(fileBytes, maxCharLimit, extractedPages, textBuilder);
            } else if (isPptxResource(lowerUrl, contentType, matType)) {
                extractPptxText(fileBytes, pages, maxCharLimit, extractedPages, textBuilder);
            } else if (isZipOfficeMagic(fileBytes)) {
                // Archivo OOXML sin extensión explícita: intentar DOCX y luego PPTX
                try {
                    extractDocxText(fileBytes, maxCharLimit, extractedPages, textBuilder);
                } catch (Exception docxEx) {
                    textBuilder.setLength(0);
                    extractedPages.clear();
                    extractPptxText(fileBytes, pages, maxCharLimit, extractedPages, textBuilder);
                }
            } else if (contentType.contains("text/") || lowerUrl.endsWith(".txt") || lowerUrl.endsWith(".md") || lowerUrl.endsWith(".csv")) {
                String plain = new String(fileBytes, StandardCharsets.UTF_8).trim();
                if (!plain.isEmpty()) {
                    extractedPages.add(1);
                    textBuilder.append(plain);
                }
            } else {
                throw new UpstreamServiceException("Formato de archivo no soportado para extracción automática: "
                        + (matType.isBlank() ? contentType : matType));
            }

            if (textBuilder.length() > maxCharLimit) {
                textBuilder.setLength(maxCharLimit);
                textBuilder.append("\n\n... [Contenido truncado por límite de caracteres]");
            }

            if (textBuilder.length() == 0) {
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
                .materialId(matchedMaterial.getId() != null ? matchedMaterial.getId() : materialId)
                .title(matchedMaterial.getTitle())
                .url(fileUrl)
                .pagesExtracted(extractedPages)
                .totalLength(textBuilder.length())
                .contentMarkdown(textBuilder.toString())
                .quality(quality)
                .build();
    }

    private URI validateExternalMaterialUrl(String fileUrl) {
        try {
            URI uri = URI.create(fileUrl);
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
            if (!host.endsWith(".amazonaws.com") && !host.endsWith(".cloudfront.net") && !host.endsWith("utp.edu.pe")) {
                throw new SecurityException("Host no autorizado para descarga de materiales: " + host);
            }
            return uri;
        } catch (SecurityException se) {
            throw se;
        } catch (IllegalArgumentException e) {
            throw new SecurityException("URL de material inválida: " + fileUrl);
        }
    }

    private SectionMaterial findMaterialInTree(SectionCurriculumTree curriculum, String materialQuery) {
        if (curriculum == null || curriculum.getUnits() == null) return null;
        String qLower = materialQuery.trim().toLowerCase(Locale.ROOT);
        SectionMaterial fuzzyMatch = null;
        for (SectionUnit u : curriculum.getUnits()) {
            if (u.getWeeks() == null) continue;
            for (SectionWeek w : u.getWeeks()) {
                if (w.getTopics() == null) continue;
                for (SectionTopic t : w.getTopics()) {
                    if (t.getMaterials() == null) continue;
                    for (SectionMaterial m : t.getMaterials()) {
                        if (m.getId() != null && m.getId().equalsIgnoreCase(materialQuery.trim())) {
                            return m;
                        }
                        if (fuzzyMatch == null && m.getTitle() != null && m.getTitle().toLowerCase(Locale.ROOT).contains(qLower)) {
                            fuzzyMatch = m;
                        }
                    }
                }
            }
        }
        return fuzzyMatch;
    }

    private SectionMaterial findMaterialInHomeworkAttachments(String sectionId, String materialQuery, String token) {
        try {
            com.fasterxml.jackson.databind.JsonNode fullTree = utpPortalGatewayPort.fetchSectionFull(sectionId, token);
            if (fullTree == null || fullTree.isNull()) return null;
            String qLower = materialQuery.trim().toLowerCase(Locale.ROOT);
            com.fasterxml.jackson.databind.JsonNode unities = fullTree.has("unities") ? fullTree.path("unities") : fullTree.path("units");
            if (!unities.isArray()) return null;
            for (com.fasterxml.jackson.databind.JsonNode u : unities) {
                com.fasterxml.jackson.databind.JsonNode themes = u.has("themes") ? u.path("themes") : u.path("weeks");
                if (!themes.isArray()) continue;
                for (com.fasterxml.jackson.databind.JsonNode theme : themes) {
                    com.fasterxml.jackson.databind.JsonNode contents = theme.has("contents") ? theme.path("contents") : theme.path("topics");
                    if (!contents.isArray()) continue;
                    for (com.fasterxml.jackson.databind.JsonNode c : contents) {
                        if (!"HOMEWORK".equalsIgnoreCase(c.path("type").asText(""))) continue;
                        String actId = c.path("metadata").path("activityId").asText(c.path("contentId").asText(""));
                        if (actId.isBlank()) continue;
                        TaskSpecification spec = utpPortalGatewayPort.fetchTaskSpecification(sectionId, actId, token);
                        if (spec != null && spec.getAttachments() != null) {
                            for (com.utp.horario.domain.model.TaskAttachment att : spec.getAttachments()) {
                                if ((att.getId() != null && att.getId().equalsIgnoreCase(materialQuery.trim()))
                                        || (att.getName() != null && att.getName().toLowerCase(Locale.ROOT).contains(qLower))) {
                                    return SectionMaterial.builder()
                                            .id(att.getId())
                                            .title(att.getName())
                                            .type(att.getType())
                                            .url(att.getUrl())
                                            .build();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[AcademicTaskService] Búsqueda en adjuntos de tareas omitida para {}: {}", sectionId, e.getMessage());
        }
        return null;
    }

    private static boolean isPdfResource(byte[] bytes, String lowerUrl, String contentType) {
        if (bytes != null && bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') {
            return true;
        }
        return contentType.contains("pdf") || lowerUrl.contains(".pdf");
    }

    private static boolean isDocxResource(String lowerUrl, String contentType, String matType) {
        return lowerUrl.contains(".docx")
                || contentType.contains("wordprocessingml")
                || "DOCX".equals(matType)
                || "WORD".equals(matType);
    }

    private static boolean isPptxResource(String lowerUrl, String contentType, String matType) {
        return lowerUrl.contains(".pptx")
                || contentType.contains("presentationml")
                || "PPTX".equals(matType)
                || "POWER POINT".equals(matType);
    }

    private static boolean isZipOfficeMagic(byte[] bytes) {
        return bytes != null && bytes.length >= 2 && bytes[0] == 'P' && bytes[1] == 'K';
    }

    private void extractPdfText(byte[] pdfBytes, String pages, int maxCharLimit,
                                List<Integer> extractedPages, StringBuilder textBuilder) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdfBytes)) {
            int totalPages = document.getNumberOfPages();
            int startPage = 1;
            int endPage = Math.min(10, totalPages);

            if (pages != null && !pages.isBlank() && !"all".equalsIgnoreCase(pages.trim())) {
                String[] parts = pages.split("-");
                try {
                    startPage = Math.max(1, Integer.parseInt(parts[0].trim()));
                    if (parts.length > 1) {
                        endPage = Math.min(totalPages, Integer.parseInt(parts[1].trim()));
                    } else {
                        endPage = Math.min(totalPages, startPage);
                    }
                } catch (NumberFormatException ignored) {}
            } else if ("all".equalsIgnoreCase(pages != null ? pages.trim() : "")) {
                endPage = totalPages;
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
    }

    private void extractDocxText(byte[] docxBytes, int maxCharLimit,
                                 List<Integer> extractedPages, StringBuilder textBuilder) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            extractedPages.add(1);
            for (IBodyElement elem : doc.getBodyElements()) {
                if (textBuilder.length() >= maxCharLimit) break;
                if (elem instanceof XWPFParagraph p) {
                    String txt = p.getText() != null ? p.getText().trim() : "";
                    if (txt.isEmpty()) continue;
                    String style = p.getStyle() != null ? p.getStyle().toLowerCase(Locale.ROOT) : "";
                    if (style.contains("heading1") || style.contains("title") || style.contains("título 1")) {
                        textBuilder.append("## ").append(txt).append("\n\n");
                    } else if (style.contains("heading") || style.contains("título")) {
                        textBuilder.append("### ").append(txt).append("\n\n");
                    } else if (p.getNumIlvl() != null) {
                        textBuilder.append("- ").append(txt).append("\n");
                    } else {
                        textBuilder.append(txt).append("\n\n");
                    }
                } else if (elem instanceof XWPFTable table) {
                    List<XWPFTableRow> rows = table.getRows();
                    for (int r = 0; r < rows.size(); r++) {
                        List<XWPFTableCell> cells = rows.get(r).getTableCells();
                        String rowText = cells.stream()
                                .map(c -> c.getText() != null ? c.getText().replaceAll("\\s+", " ").trim() : "")
                                .collect(Collectors.joining(" | "));
                        textBuilder.append("| ").append(rowText).append(" |\n");
                        if (r == 0 && !cells.isEmpty()) {
                            String sep = cells.stream().map(c -> "---").collect(Collectors.joining(" | "));
                            textBuilder.append("| ").append(sep).append(" |\n");
                        }
                    }
                    textBuilder.append("\n");
                }
            }
        }
    }

    private void extractPptxText(byte[] pptxBytes, String pages, int maxCharLimit,
                                 List<Integer> extractedPages, StringBuilder textBuilder) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(pptxBytes))) {
            List<XSLFSlide> slides = ppt.getSlides();
            int totalSlides = slides.size();
            int startSlide = 1;
            int endSlide = Math.min(20, totalSlides);
            if (pages != null && !pages.isBlank() && !"all".equalsIgnoreCase(pages.trim())) {
                String[] parts = pages.split("-");
                try {
                    startSlide = Math.max(1, Integer.parseInt(parts[0].trim()));
                    endSlide = parts.length > 1
                            ? Math.min(totalSlides, Integer.parseInt(parts[1].trim()))
                            : Math.min(totalSlides, startSlide);
                } catch (NumberFormatException ignored) {}
            } else if ("all".equalsIgnoreCase(pages != null ? pages.trim() : "")) {
                endSlide = totalSlides;
            }

            for (int idx = startSlide; idx <= endSlide; idx++) {
                if (textBuilder.length() >= maxCharLimit) break;
                XSLFSlide slide = slides.get(idx - 1);
                StringBuilder slideContent = new StringBuilder();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String t = textShape.getText();
                        if (t != null && !t.isBlank()) {
                            slideContent.append(t.trim()).append("\n");
                        }
                    } else if (shape instanceof XSLFTable table) {
                        for (int r = 0; r < table.getRows().size(); r++) {
                            String row = table.getRows().get(r).getCells().stream()
                                    .map(c -> c.getText() != null ? c.getText().replaceAll("\\s+", " ").trim() : "")
                                    .collect(Collectors.joining(" | "));
                            slideContent.append("| ").append(row).append(" |\n");
                        }
                    }
                }
                XSLFNotes notes = slide.getNotes();
                if (notes != null) {
                    for (XSLFShape nShape : notes.getShapes()) {
                        if (nShape instanceof XSLFTextShape noteText) {
                            String nt = noteText.getText();
                            if (nt != null && !nt.isBlank() && !nt.trim().equals(String.valueOf(idx))) {
                                slideContent.append("_Notas:_ ").append(nt.trim()).append("\n");
                            }
                        }
                    }
                }
                if (slideContent.length() > 0) {
                    extractedPages.add(idx);
                    textBuilder.append("### Diapositiva ").append(idx).append("\n\n").append(slideContent).append("\n");
                }
            }
        }
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
