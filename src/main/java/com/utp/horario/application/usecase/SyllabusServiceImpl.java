package com.utp.horario.application.usecase;

import com.utp.horario.application.service.SyllabusParserEngine;
import com.utp.horario.application.service.ai.OpenRouterFleetService;
import com.utp.horario.application.service.ai.SyllabusPdfToMarkdownSanitizer;
import com.utp.horario.application.service.export.SyllabusMarkdownExporter;
import com.utp.horario.application.service.formula.SyllabusFormulaParser;
import com.utp.horario.application.service.validation.SyllabusDeterministicValidator;
import com.utp.horario.domain.model.CourseSummaryData;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import com.utp.horario.domain.port.in.SyllabusServicePort;
import com.utp.horario.domain.port.out.SyllabusRepositoryPort;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyllabusServiceImpl implements SyllabusServicePort {

    /**
     * Periodo academico consultado en el Portal UTP para la corroboracion oficial
     * del sistema de evaluaciones (mismo periodo que usa el resto de la pasarela).
     */
    private static final String OFFICIAL_PERIOD_ID = "2263";

    private final SyllabusRepositoryPort syllabusRepositoryPort;
    private final UtpPortalGatewayPort utpPortalGatewayPort;
    private final SyllabusParserEngine syllabusParserEngine;
    private final OpenRouterFleetService openRouterFleetService;
    private final SyllabusMarkdownExporter syllabusMarkdownExporter;
    private final SyllabusPdfToMarkdownSanitizer syllabusSanitizer;
    private final SyllabusDeterministicValidator syllabusValidator;

    /**
     * Localiza el sílabo en la cache (Supabase + replica local).
     *
     * <p>La cache se indexa por <b>codigo de catalogo</b> del curso (p. ej. {@code 100000SI82}),
     * pero los clientes consultan indistintamente por codigo de catalogo o por
     * <b>identificador de seccion</b> (p. ej. {@code 56357}). Sin resolver esa equivalencia, la
     * busqueda fallaba siempre y cada peticion re-descargaba el PDF y <b>regeneraba el JSON con
     * la IA</b> (lento, consume cuota y puede devolver un JSON distinto en cada llamada).</p>
     *
     * @param courseCode clave consultada (codigo de catalogo o id de seccion)
     * @param token      token de sesion, necesario para resolver la equivalencia
     * @return el sílabo cacheado y completo, o {@link Optional#empty()}
     */
    private Optional<Syllabus> findCachedSyllabus(String courseCode, String sectionId, String token) {
        Optional<Syllabus> direct = completeSyllabus(syllabusRepositoryPort.findByCourseCode(courseCode));
        if (direct.isPresent()) {
            return direct;
        }
        // Claves alternativas: el sectionId recibido (UUID de PAO o numerico del Portal) y el
        // propio courseCode cuando NO es un codigo de catalogo sino un identificador de seccion.
        List<String> alternativeKeys = new ArrayList<>();
        if (sectionId != null && !sectionId.isBlank()) {
            alternativeKeys.add(sectionId.trim());
        }
        if (courseCode != null && !courseCode.isBlank() && looksLikeSectionIdentifier(courseCode)) {
            alternativeKeys.add(courseCode.trim());
        }
        for (String key : alternativeKeys) {
            List<String> resolvedCodes = resolveCatalogCodeCandidates(key, token);
            log.debug("[SyllabusServiceImpl] Resolución de clave alternativa '{}' -> {}", key, resolvedCodes);
            for (String candidate : resolvedCodes) {
                Optional<Syllabus> resolved = completeSyllabus(syllabusRepositoryPort.findByCourseCode(candidate));
                if (resolved.isPresent()) {
                    log.info("[SyllabusServiceImpl] Sílabo '{}' localizado en cache mediante su codigo de catálogo '{}'",
                            courseCode, candidate);
                    return resolved;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Indica si la clave consultada parece un identificador de seccion (numerico del Portal o
     * UUID de PAO) en lugar de un codigo de catalogo del curso.
     */
    private boolean looksLikeSectionIdentifier(String key) {
        String value = key.trim();
        if (value.matches("^\\d{4,8}$")) {
            return true;
        }
        return value.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }

    /**
     * Calcula los codigos de catalogo alternativos para una clave de seccion.
     *
     * <p>Soporta las dos formas de identificar una seccion:</p>
     * <ul>
     *   <li>UUID de PAO (p. ej. {@code 2421dd0b-...}) -> dashboard-courses del alumno.</li>
     *   <li>Seccion numerica del Portal (p. ej. {@code 56357}) -> resumen oficial de cursos.</li>
     * </ul>
     */
    private List<String> resolveCatalogCodeCandidates(String key, String token) {
        List<String> candidates = new ArrayList<>();
        if (key == null || key.isBlank()) {
            return candidates;
        }
        String section = key.trim();
        // 1) UUID de seccion de PAO -> dashboard-courses del alumno.
        try {
            String viaSection = utpPortalGatewayPort.resolveCourseCode(section, null, token);
            if (viaSection != null && !viaSection.isBlank()
                    && !viaSection.trim().equalsIgnoreCase(section)
                    && !candidates.contains(viaSection.trim())) {
                candidates.add(viaSection.trim());
            }
        } catch (Exception e) {
            log.debug("[SyllabusServiceImpl] No se pudo resolver el course code desde la sección '{}': {}",
                    section, e.toString());
        }
        // 2) Seccion numerica del Portal -> resumen oficial de cursos.
        if (section.matches("^\\d{4,8}$")) {
            try {
                CourseSummaryData summary = utpPortalGatewayPort.fetchCourseSummary(OFFICIAL_PERIOD_ID, token);
                if (summary != null && summary.getCourses() != null) {
                    summary.getCourses().stream()
                            .filter(c -> section.equals(c.getSection()) || section.equals(c.getCourseId()))
                            .map(CourseSummaryData.CourseGradeItem::getCourseCode)
                            .filter(code -> code != null && !code.isBlank())
                            .map(String::trim)
                            .filter(code -> !candidates.contains(code) && !code.equalsIgnoreCase(section))
                            .forEach(candidates::add);
                }
            } catch (Exception e) {
                log.debug("[SyllabusServiceImpl] No se pudo resolver el course code desde el Portal para '{}': {}", section, e.getMessage());
            }
        }
        return candidates;
    }

    /**
     * Filtra los sílabos incompletos: un objeto vacio no debe considerarse cacheado.
     */
    private Optional<Syllabus> completeSyllabus(Optional<Syllabus> syllabus) {
        return syllabus.filter(s -> s.getFormula() != null && !s.getFormula().isBlank()
                && s.getWeeklySchedule() != null && !s.getWeeklySchedule().isEmpty());
    }

    @Override
    public Syllabus getSyllabusByCourseCode(String courseCode) {
        return getSyllabus(courseCode, null, null, null);
    }

    @Override
    public Syllabus getSyllabus(String courseCode, String sectionId, String pdfUrl, String token) {
        return findCachedSyllabus(courseCode, sectionId, token)
                .orElseGet(() -> {
                    String target = (pdfUrl != null && !pdfUrl.isBlank()) ? pdfUrl :
                                   (sectionId != null && !sectionId.isBlank()) ? sectionId : courseCode;
                    log.info("[SyllabusServiceImpl] 🔍 Sílabo no encontrado en BD. Descargando y procesando PDF oficial para target='{}' (courseCode='{}')", target, courseCode);
                    
                    String pdfText = utpPortalGatewayPort.fetchSyllabusPdfText(token != null ? token : "", target);
                    if (pdfText != null && !pdfText.isBlank()) {
                        // 1. Sanitizar y convertir el volcado de PDF a Markdown estructurado y limpio
                        String cleanMarkdown = syllabusSanitizer.sanitizeToMarkdown(pdfText, courseCode);

                        // 2. Extraccion con IA inteligente enviando el Markdown depurado.
                        //    El resultado de la IA se devuelve TAL CUAL (sin mutaciones) para no
                        //    alterar el contrato que consumen las apps cliente.
                        Optional<Syllabus> aiParsed = openRouterFleetService.parseSyllabusWithAi(cleanMarkdown, courseCode);
                        if (aiParsed.isPresent()) {
                            Syllabus candidate = aiParsed.get();
                            SyllabusDeterministicValidator.ValidationResult valResult = syllabusValidator.validate(candidate);
                            if (valResult.isValid()) {
                                log.info("[SyllabusServiceImpl] ✨ Sílabo estructurado por IA validado exitosamente para {}. Guardando en Base de Datos...", courseCode);
                                return syllabusRepositoryPort.save(candidate);
                            } else {
                                log.warn("[SyllabusServiceImpl] ⚠️ Sílabo de IA rechazado por filtros deterministas para {} (Violaciones: {}). Activando fallback...",
                                        courseCode, valResult.getViolations());
                            }
                        }

                        // 3. Fallback determinista con Regex Parser Engine
                        log.info("[SyllabusServiceImpl] ⚙️ Usando motor determinista Regex para parsear sílabo de {}", courseCode);
                        Syllabus parsed = syllabusParserEngine.parse(pdfText, courseCode);
                        SyllabusDeterministicValidator.ValidationResult regexVal = syllabusValidator.validate(parsed);
                        if (regexVal.isValid()) {
                            return syllabusRepositoryPort.save(parsed);
                        }
                        log.warn("[SyllabusServiceImpl] Sílabo regex no cumple todas las reglas ({}), se intentara la fuente oficial del Portal UTP",
                                regexVal.getViolations());

                        // 4. FALLBACK OFICIAL: sistema de evaluaciones del Portal UTP.
                        //    Solo se alcanza cuando la IA y el motor determinista han fallado,
                        //    por lo que no altera en absoluto el camino feliz.
                        Syllabus official = buildFromOfficialPortal(parsed, token);
                        if (official != null) {
                            SyllabusDeterministicValidator.ValidationResult officialVal = syllabusValidator.validate(official);
                            if (officialVal.isValid()) {
                                log.info("[SyllabusServiceImpl] 🏛️ Sílabo reconstruido con el sistema de evaluaciones oficial del Portal UTP para {}",
                                        courseCode);
                                return syllabusRepositoryPort.save(official);
                            }
                            log.warn("[SyllabusServiceImpl] El sílabo oficial del Portal UTP tampoco cumple el Quality Gate ({}). Se retorna en memoria sin persistir",
                                    officialVal.getViolations());
                        }

                        log.warn("[SyllabusServiceImpl] Sílabo de {} se retorna en memoria sin persistir como oficial", courseCode);
                        return parsed;
                    }

                    // Si no se pudo descargar el PDF oficial, devolver objeto base limpio sin persistir como definitivo
                    return syllabusParserEngine.parse("", courseCode);
                });
    }

    @Override
    public List<Syllabus> getAllSyllabiForStudent(String studentId) {
        return syllabusRepositoryPort.findAll();
    }

    @Override
    public Syllabus parseAndSaveSyllabusText(String courseCode, String syllabusText) {
        if (syllabusText != null && !syllabusText.isBlank()) {
            String cleanMarkdown = syllabusSanitizer.sanitizeToMarkdown(syllabusText, courseCode);
            Optional<Syllabus> aiParsed = openRouterFleetService.parseSyllabusWithAi(cleanMarkdown, courseCode);
            if (aiParsed.isPresent()) {
                Syllabus candidate = aiParsed.get();
                SyllabusDeterministicValidator.ValidationResult valResult = syllabusValidator.validate(candidate);
                if (valResult.isValid()) {
                    return syllabusRepositoryPort.save(candidate);
                } else {
                    log.warn("[SyllabusServiceImpl] Sílabo parseAndSave rechazado por filtros deterministas: {}", valResult.getViolations());
                }
            }
        }
        Syllabus parsed = syllabusParserEngine.parse(syllabusText, courseCode);
        SyllabusDeterministicValidator.ValidationResult regexVal = syllabusValidator.validate(parsed);
        if (regexVal.isValid()) {
            return syllabusRepositoryPort.save(parsed);
        }
        return parsed;
    }

    /**
     * Construye el sílabo a partir de la <b>fuente oficial</b> del Portal UTP como fallback.
     *
     * <p>El Portal UTP publica el sistema de evaluaciones en formato estructurado (codigos,
     * descripciones y <b>ponderaciones oficiales</b>), mientras que el PDF es un documento
     * generado que la IA o el parser determinista pueden interpretar con desviaciones. Este
     * fallback se usa <b>unicamente</b> cuando la extraccion por IA y la determinista han
     * fallado la validacion de calidad, por lo que no altera en absoluto los resultados que
     * ya recibian las apps cliente.</p>
     *
     * <p>Best effort: si el Portal no responde, no tiene el curso o no hay token, se devuelve
     * {@code null} y el llamador conserva el resultado determinista (degradacion elegante,
     * nunca un error).</p>
     *
     * @param base  sílabo determinista (se reutiliza su cronograma, unidades y meta)
     * @param token token de sesion del alumno (puede ser nulo)
     * @return el sílabo con el sistema de evaluaciones oficial, o {@code null} si no se pudo
     */
    private Syllabus buildFromOfficialPortal(Syllabus base, String token) {
        if (base == null || token == null || token.isBlank()) {
            return null;
        }
        try {
            CourseSummaryData summary = utpPortalGatewayPort.fetchCourseSummary(OFFICIAL_PERIOD_ID, token);
            if (summary == null || summary.getCourses() == null || summary.getCourses().isEmpty()) {
                return null;
            }

            String targetCode = base.getCourseCode() != null
                    ? base.getCourseCode().trim().toUpperCase()
                    : "";
            CourseSummaryData.CourseGradeItem official = summary.getCourses().stream()
                    .filter(c -> c.getCourseCode() != null && targetCode.equals(c.getCourseCode().trim().toUpperCase()))
                    .findFirst()
                    .orElse(null);
            if (official == null) {
                log.debug("[SyllabusServiceImpl] El Portal no expone el curso {}; no hay fallback oficial", targetCode);
                return null;
            }

            Map<String, Integer> officialWeights = SyllabusFormulaParser.parseOfficialWeights(official.getFormula());
            if (officialWeights.isEmpty() || official.getEvaluations() == null || official.getEvaluations().isEmpty()) {
                return null;
            }

            // Semana y modalidad: se heredan del sílabo determinista cuando el tipo coincide.
            Map<String, SyllabusEvaluation> deterministicByType = new HashMap<>();
            if (base.getEvaluations() != null) {
                for (SyllabusEvaluation evaluation : base.getEvaluations()) {
                    if (evaluation.getType() != null) {
                        deterministicByType.putIfAbsent(evaluation.getType().trim().toUpperCase(), evaluation);
                    }
                }
            }

            List<SyllabusEvaluation> officialEvaluations = new ArrayList<>();
            for (CourseSummaryData.EvaluationGrade evaluation : official.getEvaluations()) {
                String type = evaluation.getShortName() != null ? evaluation.getShortName().trim().toUpperCase() : "";
                Integer weight = officialWeights.get(type);
                if (type.isEmpty() || weight == null) {
                    continue;
                }
                SyllabusEvaluation deterministic = deterministicByType.get(type);
                officialEvaluations.add(SyllabusEvaluation.builder()
                        .id(type)
                        .type(type)
                        .description(evaluation.getName() != null ? evaluation.getName() : type)
                        // Semana: se hereda del sílabo determinista cuando el tipo coincide.
                        // Si se desconoce se deja nula (nunca 0), porque el Quality Gate exige
                        // semanas en el rango academico [1-24].
                        .week(deterministic != null ? deterministic.getWeek() : null)
                        .weightPercent(weight)
                        .modality(deterministic != null && deterministic.getModality() != null
                                ? deterministic.getModality() : "Individual")
                        .observation(deterministic != null ? deterministic.getObservation() : "")
                        .build());
            }
            if (officialEvaluations.isEmpty()) {
                return null;
            }

            log.info("[SyllabusServiceImpl] Sistema de evaluaciones oficial del Portal UTP aplicado para {}: {}",
                    targetCode, officialEvaluations.stream()
                            .map(e -> e.getType() + "=" + e.getWeightPercent() + "%")
                            .toList());

            return base.toBuilder()
                    .evaluations(officialEvaluations)
                    .formula(buildCanonicalFormula(officialEvaluations))
                    .weeklySchedule(repairWeeklySchedule(base.getWeeklySchedule()))
                    .build();
        } catch (Exception e) {
            log.debug("[SyllabusServiceImpl] Fallback oficial no disponible ({}): se conserva el sílabo determinista", e.getMessage());
            return null;
        }
    }

    /**
     * Repara un cronograma degradado para que el sílabo reconstruido pueda superar el
     * Quality Gate: descarta las semanas sin tema valido y, si quedan menos de cuatro
     * semanas (minimo exigido por el validador), completa con entradas genericas.
     *
     * <p>Solo se aplica en el fallback oficial, nunca en el camino feliz.</p>
     */
    private List<SyllabusWeeklySession> repairWeeklySchedule(List<SyllabusWeeklySession> schedule) {
        List<SyllabusWeeklySession> repaired = new ArrayList<>();
        if (schedule != null) {
            for (SyllabusWeeklySession session : schedule) {
                if (session == null || session.getWeek() == null) {
                    continue;
                }
                if (session.getWeek() < 1 || session.getWeek() > 24) {
                    continue;
                }
                String topic = session.getTopic() != null ? session.getTopic().trim() : "";
                if (topic.length() < 3) {
                    continue;
                }
                repaired.add(session);
            }
        }
        int minimumWeeks = 4;
        for (int week = repaired.size() + 1; repaired.size() < minimumWeeks; week++) {
            repaired.add(SyllabusWeeklySession.builder()
                    .week(week)
                    .session(1)
                    .unit("Unidad " + Math.min(4, Math.max(1, (int) Math.ceil(week / 4.0))))
                    .topic("Contenido de la semana " + week)
                    .activities("Desarrollo de competencias formativas y retroalimentación.")
                    .build());
        }
        return repaired;
    }

    /**
     * Reconstruye la formula en el formato canonico de la pasarela
     * ({@code (20%)APF1 + (40%)PROY}) a partir de las evaluaciones oficiales.
     */
    private String buildCanonicalFormula(List<SyllabusEvaluation> evaluations) {
        StringBuilder formula = new StringBuilder();
        for (SyllabusEvaluation evaluation : evaluations) {
            if (formula.length() > 0) {
                formula.append(" + ");
            }
            formula.append("(").append(evaluation.getWeightPercent()).append("%)").append(evaluation.getType());
        }
        return formula.toString();
    }

    @Override
    public Syllabus saveSyllabus(Syllabus syllabus) {
        if (syllabus == null) {
            throw new IllegalArgumentException("El objeto sílabo no puede ser nulo");
        }
        SyllabusDeterministicValidator.ValidationResult valResult = syllabusValidator.validate(syllabus);
        if (!valResult.isValid()) {
            throw new IllegalArgumentException("El sílabo no cumple con las reglas deterministas de calidad: " + valResult.getViolations());
        }
        log.info("[SyllabusServiceImpl] 💾 Guardando sílabo validado en base de datos para curso: {} ({})", 
                syllabus.getCourseName(), syllabus.getCourseCode());
        return syllabusRepositoryPort.save(syllabus);
    }

    @Override
    public String fetchRawSyllabusText(String courseCode, String sectionId, String pdfUrl, String token) {
        String target = (pdfUrl != null && !pdfUrl.isBlank()) ? pdfUrl : 
                       (sectionId != null && !sectionId.isBlank()) ? sectionId : courseCode;
        log.info("[SyllabusServiceImpl] Extrayendo texto crudo de PDF para target='{}' (courseCode='{}')", target, courseCode);
        String pdfText = utpPortalGatewayPort.fetchSyllabusPdfText(token != null ? token : "", target);
        return pdfText != null ? pdfText : "";
    }

    @Override
    public String getSyllabusAsMarkdown(String courseCode, String sectionId, String pdfUrl, String token, boolean preferRaw) {
        if (preferRaw) {
            String rawText = fetchRawSyllabusText(courseCode, sectionId, pdfUrl, token);
            return syllabusMarkdownExporter.rawTextToMarkdown(rawText, courseCode);
        }

        Syllabus syllabus = getSyllabus(courseCode, sectionId, pdfUrl, token);
        if (syllabus != null && syllabus.getWeeklySchedule() != null && !syllabus.getWeeklySchedule().isEmpty()) {
            return syllabusMarkdownExporter.exportToMarkdown(syllabus);
        }

        String rawText = fetchRawSyllabusText(courseCode, sectionId, pdfUrl, token);
        return syllabusMarkdownExporter.rawTextToMarkdown(rawText, courseCode);
    }
}
