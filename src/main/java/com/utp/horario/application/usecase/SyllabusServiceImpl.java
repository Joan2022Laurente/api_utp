package com.utp.horario.application.usecase;

import com.utp.horario.application.service.SyllabusParserEngine;
import com.utp.horario.application.service.ai.OpenRouterFleetService;
import com.utp.horario.application.service.ai.SyllabusPdfToMarkdownSanitizer;
import com.utp.horario.application.service.export.SyllabusMarkdownExporter;
import com.utp.horario.application.service.validation.SyllabusDeterministicValidator;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.port.in.SyllabusServicePort;
import com.utp.horario.domain.port.out.SyllabusRepositoryPort;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyllabusServiceImpl implements SyllabusServicePort {

    private final SyllabusRepositoryPort syllabusRepositoryPort;
    private final UtpPortalGatewayPort utpPortalGatewayPort;
    private final SyllabusParserEngine syllabusParserEngine;
    private final OpenRouterFleetService openRouterFleetService;
    private final SyllabusMarkdownExporter syllabusMarkdownExporter;
    private final SyllabusPdfToMarkdownSanitizer syllabusSanitizer;
    private final SyllabusDeterministicValidator syllabusValidator;

    @Override
    public Syllabus getSyllabusByCourseCode(String courseCode) {
        return getSyllabus(courseCode, null, null, null);
    }

    @Override
    public Syllabus getSyllabus(String courseCode, String sectionId, String pdfUrl, String token) {
        return syllabusRepositoryPort.findByCourseCode(courseCode)
                .filter(s -> s.getFormula() != null && !s.getFormula().isBlank() && s.getWeeklySchedule() != null && !s.getWeeklySchedule().isEmpty())
                .orElseGet(() -> {
                    String target = (pdfUrl != null && !pdfUrl.isBlank()) ? pdfUrl : 
                                   (sectionId != null && !sectionId.isBlank()) ? sectionId : courseCode;
                    log.info("[SyllabusServiceImpl] 🔍 Sílabo no encontrado en BD. Descargando y procesando PDF oficial para target='{}' (courseCode='{}')", target, courseCode);
                    
                    String pdfText = utpPortalGatewayPort.fetchSyllabusPdfText(token != null ? token : "", target);
                    if (pdfText != null && !pdfText.isBlank()) {
                        // 1. Sanitizar y convertir el volcado de PDF a Markdown estructurado y limpio
                        String cleanMarkdown = syllabusSanitizer.sanitizeToMarkdown(pdfText, courseCode);

                        // 2. Extraer con IA inteligente enviando el Markdown depurado
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
                        } else {
                            log.warn("[SyllabusServiceImpl] Sílabo regex no cumple todas las reglas ({}), se retorna en memoria sin persistir como oficial", regexVal.getViolations());
                            return parsed;
                        }
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
