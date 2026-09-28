package com.utp.horario.application.usecase;

import com.utp.horario.application.service.SyllabusParserEngine;
import com.utp.horario.application.service.ai.OpenRouterFleetService;
import com.utp.horario.application.service.export.SyllabusMarkdownExporter;
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
                        // 1. Intentar extracción con IA inteligente (OpenRouter multi-account fallback)
                        Optional<Syllabus> aiParsed = openRouterFleetService.parseSyllabusWithAi(pdfText, courseCode);
                        if (aiParsed.isPresent() && isComplete(aiParsed.get())) {
                            log.info("[SyllabusServiceImpl] ✨ Sílabo estructurado por IA para {}. Guardando en Base de Datos...", courseCode);
                            return syllabusRepositoryPort.save(aiParsed.get());
                        }

                        // 2. Fallback determinista con Regex Parser Engine
                        log.info("[SyllabusServiceImpl] ⚙️ Usando motor determinista Regex para parsear sílabo de {}", courseCode);
                        Syllabus parsed = syllabusParserEngine.parse(pdfText, courseCode);
                        return syllabusRepositoryPort.save(parsed);
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
            Optional<Syllabus> aiParsed = openRouterFleetService.parseSyllabusWithAi(syllabusText, courseCode);
            if (aiParsed.isPresent() && isComplete(aiParsed.get())) {
                return syllabusRepositoryPort.save(aiParsed.get());
            }
        }
        Syllabus parsed = syllabusParserEngine.parse(syllabusText, courseCode);
        return syllabusRepositoryPort.save(parsed);
    }

    @Override
    public Syllabus saveSyllabus(Syllabus syllabus) {
        if (syllabus == null) {
            throw new IllegalArgumentException("El objeto sílabo no puede ser nulo");
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

    private boolean isComplete(Syllabus s) {
        return (s.getWeeklySchedule() != null && !s.getWeeklySchedule().isEmpty())
                && (s.getEvaluations() != null && !s.getEvaluations().isEmpty());
    }
}
