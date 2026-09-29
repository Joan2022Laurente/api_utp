package com.utp.horario.application.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.application.service.SyllabusParserEngine;
import com.utp.horario.application.service.export.SyllabusMarkdownExporter;
import com.utp.horario.application.service.validation.SyllabusDeterministicValidator;
import com.utp.horario.application.usecase.SyllabusServiceImpl;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import com.utp.horario.infrastructure.config.ai.OpenRouterProperties;
import com.utp.horario.infrastructure.config.supabase.SupabaseProperties;
import com.utp.horario.infrastructure.persistence.adapter.SyllabusRepositoryAdapter;
import com.utp.horario.infrastructure.persistence.repository.SpringDataSyllabusRepository;
import com.utp.horario.infrastructure.persistence.supabase.SupabaseSyllabusClient;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test de Integración: Poblamiento real de la Base de Datos Dedicada de la API
 * y análisis exhaustivo de calidad, consistencia y detección de anomalías.
 */
public class SyllabusPopulationAndAnalysisIntegrationTest {

    private SupabaseProperties supabaseProperties;
    private SupabaseSyllabusClient supabaseClient;
    private OpenRouterProperties openRouterProperties;
    private OpenRouterFleetService openRouterFleetService;
    private SyllabusRepositoryAdapter repositoryAdapter;
    private SyllabusServiceImpl syllabusService;
    private SyllabusDeterministicValidator validator;
    private ObjectMapper objectMapper;

    private static final String SAMPLES_DIR = "src/test/resources/silabos";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // 1. Supabase Client con BD Dedicada
        supabaseProperties = new SupabaseProperties();
        supabaseProperties.initFallbackKeys();
        supabaseClient = new SupabaseSyllabusClient(supabaseProperties, objectMapper);

        // 2. OpenRouter Fleet Service
        openRouterProperties = new OpenRouterProperties();
        openRouterProperties.initFleetKeys();
        OpenRouterModelSelector modelSelector = new OpenRouterModelSelector(openRouterProperties, objectMapper);
        openRouterFleetService = new OpenRouterFleetService(openRouterProperties, modelSelector, objectMapper);

        // 3. Mocks de Gateway y Repo Spring Data
        SpringDataSyllabusRepository springDataRepoMock = Mockito.mock(SpringDataSyllabusRepository.class);
        when(springDataRepoMock.searchSyllabus(anyString())).thenReturn(List.of());
        when(springDataRepoMock.findById(anyString())).thenReturn(Optional.empty());

        UtpPortalGatewayPort gatewayPortMock = Mockito.mock(UtpPortalGatewayPort.class);

        // 4. Componentes y Casos de Uso
        repositoryAdapter = new SyllabusRepositoryAdapter(springDataRepoMock, supabaseClient, objectMapper);
        SyllabusParserEngine parserEngine = new SyllabusParserEngine();
        SyllabusMarkdownExporter markdownExporter = new SyllabusMarkdownExporter();
        SyllabusPdfToMarkdownSanitizer sanitizer = new SyllabusPdfToMarkdownSanitizer();
        validator = new SyllabusDeterministicValidator();

        syllabusService = new SyllabusServiceImpl(
                repositoryAdapter,
                gatewayPortMock,
                parserEngine,
                openRouterFleetService,
                markdownExporter,
                sanitizer,
                validator
        );
    }

    private String extractTextFromPdf(String fileName) throws IOException {
        Path path = Paths.get(SAMPLES_DIR, fileName).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Archivo PDF no encontrado: " + path);
        }
        byte[] bytes = Files.readAllBytes(path);
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    @Test
    @DisplayName("Poblar BD Dedicada con Sílabos Oficiales y Ejecutar Análisis Exhaustivo de Calidad")
    void populateAndAnalyzeDedicatedDatabase() throws Exception {
        System.out.println("================================================================================");
        System.out.println("🚀 INICIANDO POBLAMIENTO Y ANÁLISIS DE SÍLABOS EN BD DEDICADA");
        System.out.println("   URL Supabase: " + supabaseProperties.getUrl());
        System.out.println("   Tabla: " + supabaseProperties.getTable());
        System.out.println("================================================================================\n");

        Map<String, String> syllabiSources = new LinkedHashMap<>();

        // 1. Extraer texto de los PDFs oficiales
        syllabiSources.put("100000ST61", extractTextFromPdf("100000ST61_DesarrolloWebIntegrado.pdf"));
        syllabiSources.put("100000SI82", extractTextFromPdf("100000SI82_FormacionParaLaInvestigacion.pdf"));
        syllabiSources.put("100000SI97", extractTextFromPdf("100000SI97_ServiciosCloud.pdf"));
        syllabiSources.put("100000S74T", extractTextFromPdf("100000S74T_GestionDelServicioTi.pdf"));
        syllabiSources.put("100000SI68", extractTextFromPdf("100000SI68_LenguajesDeProgramacion.pdf"));

        // 2. Sílabo oficial de Herramientas para la Comunicación Efectiva
        String comunicacionText = """
                SÍLABO
                HERRAMIENTAS PARA LA COMUNICACIÓN EFECTIVA (100000S72V)
                2026 - Ciclo 2 Agosto

                1. INFORMACIÓN GENERAL
                1.1. Carrera: Ingeniería de Sistemas e Informática
                1.2. Créditos: 3
                1.3. Enseñanza de curso: Virtual 24/7
                1.4. Horas semanales: 3

                4. LOGRO GENERAL DE APRENDIZAJE
                Al finalizar el curso, el estudiante aplica técnicas de comunicación efectiva tanto oral como corporal para interactuar con asertividad y seguridad en entornos académicos y profesionales.

                7. SISTEMA DE EVALUACIÓN
                El cálculo del promedio final considera la siguiente fórmula:
                (15%)AP1 + (15%)AP2 + (25%)AP3 + (20%)PA + (25%)PTF

                Donde:
                Tipo Descripción Semana Observación
                AP1 AVANCE DE PORTAFOLIO 1 3 Individual
                AP2 AVANCE DE PORTAFOLIO 2 7 Individual
                AP3 AVANCE DE PORTAFOLIO 3 12 Individual
                PA PARTICIPACIÓN EN CLASE 16 Compuesta por 4 actividades
                PTF PORTAFOLIO FINAL 18 Individual

                8. CRONOGRAMA DE ACTIVIDADES
                Semana 1
                Temario: Comunicación efectiva y su impacto en la vida académica y profesional
                Semana 2
                Temario: Creencias limitantes y su impacto en la comunicación
                Semana 3
                Temario: Identificación de comportamientos en situaciones comunicativas. Evaluación AP1
                Semana 4
                Temario: Expresión Oral: Pronunciación, tono de voz, énfasis de ideas
                Semana 5
                Temario: Expresión Oral: ejercicios de vocalización y trabalenguas
                Semana 6
                Temario: Expresión Corporal: ejercicios de gestos faciales, ademanes
                Semana 7
                Temario: Expresión Corporal: ejercicios de postura. Evaluación AP2
                Semana 8
                Temario: La Asertividad en la Comunicación Interpersonal
                Semana 9
                Temario: Habilidades comunicativas clave en la negociación
                Semana 10
                Temario: Estrategias de comunicación efectiva en acuerdos
                Semana 11
                Temario: Integración de Estrategias Comunicativas en Expresión Oral
                Semana 12
                Temario: Integración de Estrategias Comunicativas parte 2. Evaluación AP3
                Semana 13
                Temario: Estructura expositiva: introducción
                Semana 14
                Temario: Estructura expositiva: desarrollo
                Semana 15
                Temario: Estructura expositiva: conclusión
                Semana 16
                Temario: Seguridad y Naturalidad al Hablar en Público. Evaluación PA
                Semana 17
                Temario: Dominio Escénico y Manejo del Público parte 1
                Semana 18
                Temario: Dominio Escénico y Manejo del Público parte 2. Evaluación PTF
                """;
        syllabiSources.put("100000S72V", comunicacionText);

        // 3. Procesar y Guardar cada sílabo a través de la API
        Map<String, Syllabus> processedResults = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : syllabiSources.entrySet()) {
            String code = entry.getKey();
            String rawText = entry.getValue();

            System.out.println("⏳ Procesando sílabo oficial: " + code + " (longitud texto: " + rawText.length() + " chars)...");
            Syllabus saved = syllabusService.parseAndSaveSyllabusText(code, rawText);
            assertNotNull(saved, "El sílabo procesado no debe ser nulo para: " + code);
            processedResults.put(code, saved);
            System.out.println("   ✅ Persistido con éxito: " + saved.getCourseName() + " (" + saved.getCourseCode() + ")\n");
        }

        assertEquals(6, processedResults.size(), "Deben haberse procesado los 6 sílabos");

        // 4. ANÁLISIS EXHAUSTIVO DE LOS DATOS ALMACENADOS EN SUPABASE
        System.out.println("\n================================================================================");
        System.out.println("🔍 INICIANDO AUDITORÍA Y ANÁLISIS DE REGISTROS ALMACENADOS EN SUPABASE");
        System.out.println("================================================================================\n");

        int totalEvaluaciones = 0;
        int totalSemanas = 0;
        List<String> auditFindings = new ArrayList<>();

        for (String code : syllabiSources.keySet()) {
            Optional<Syllabus> fromDbOpt = supabaseClient.findByCourseCode(code);
            assertTrue(fromDbOpt.isPresent(), "El curso debe encontrarse en Supabase DB: " + code);

            Syllabus dbSyllabus = fromDbOpt.get();
            assertNotNull(dbSyllabus.getCourseName(), "Nombre del curso no debe ser nulo");
            assertNotNull(dbSyllabus.getFormula(), "Fórmula no debe ser nula");
            assertNotNull(dbSyllabus.getEvaluations(), "Evaluaciones no deben ser nulas");
            assertNotNull(dbSyllabus.getWeeklySchedule(), "Cronograma no debe ser nulo");

            // Validar Quality Gate sobre el objeto persistido
            SyllabusDeterministicValidator.ValidationResult valResult = validator.validate(dbSyllabus);
            assertTrue(valResult.isValid(), "El sílabo en BD viola el Quality Gate determinista: " + valResult.getViolations());

            // Ponderación
            int sumWeights = dbSyllabus.getEvaluations().stream()
                    .mapToInt(SyllabusEvaluation::getWeightPercent)
                    .sum();
            assertEquals(100, sumWeights, "La suma de ponderaciones para " + code + " debe ser 100%");

            // Semanas de cronograma
            assertEquals(18, dbSyllabus.getWeeklySchedule().size(), "El cronograma de " + code + " debe tener exactamente 18 semanas");

            totalEvaluaciones += dbSyllabus.getEvaluations().size();
            totalSemanas += dbSyllabus.getWeeklySchedule().size();

            // Imprimir resumen detallado
            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("CURSO EN BD: " + dbSyllabus.getCourseName() + " [" + dbSyllabus.getCourseCode() + "]");
            System.out.println("  Créditos: " + dbSyllabus.getCredits() + " | Modalidad: " + dbSyllabus.getModality() + " | Horas: " + dbSyllabus.getWeeklyHours());
            System.out.println("  Fórmula: " + dbSyllabus.getFormula());
            System.out.println("  Evaluaciones (" + dbSyllabus.getEvaluations().size() + "):");
            for (SyllabusEvaluation ev : dbSyllabus.getEvaluations()) {
                System.out.println(String.format("    - [%-5s] Sem %-2d | %-3d%% | %-10s | %s",
                        ev.getType(), ev.getWeek(), ev.getWeightPercent(), ev.getModality(), ev.getDescription()));
            }

            // Validar concordancia de evaluaciones con el cronograma
            Set<String> evalTypesInFormula = new HashSet<>();
            for (SyllabusEvaluation ev : dbSyllabus.getEvaluations()) {
                evalTypesInFormula.add(ev.getType());
                // Validar que la semana de la evaluación tenga coherencia en el cronograma
                int weekNum = ev.getWeek();
                if (weekNum >= 1 && weekNum <= 18) {
                    SyllabusWeeklySession session = dbSyllabus.getWeeklySchedule().get(weekNum - 1);
                    if (session.getEvaluation() == null || session.getEvaluation().isBlank()) {
                        auditFindings.add("Nota en " + code + ": En semana " + weekNum + " se evalúa " + ev.getType() + " pero el campo 'evaluation' del cronograma está vacío.");
                    }
                }
            }
            System.out.println();
        }

        System.out.println("================================================================================");
        System.out.println("📊 REPORTE DE AUDITORÍA Y RESULTADOS GLOBALES:");
        System.out.println("   Total Cursos Procesados y Persistidos: " + syllabiSources.size());
        System.out.println("   Total Evaluaciones Estructuradas: " + totalEvaluaciones);
        System.out.println("   Total Sesiones Semanales Validadas: " + totalSemanas);
        System.out.println("   Anomalías Críticas Encontradas: 0 (Todos los 6 sílabos pasan Quality Gate al 100%)");
        if (!auditFindings.isEmpty()) {
            System.out.println("   Observaciones Menores:");
            for (String finding : auditFindings) {
                System.out.println("     * " + finding);
            }
        }
        System.out.println("================================================================================\n");
    }
}
