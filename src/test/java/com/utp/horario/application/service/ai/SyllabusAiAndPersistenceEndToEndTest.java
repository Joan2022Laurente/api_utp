package com.utp.horario.application.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.application.service.SyllabusParserEngine;
import com.utp.horario.application.service.export.SyllabusMarkdownExporter;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SyllabusAiAndPersistenceEndToEndTest {

    private SupabaseProperties supabaseProperties;
    private SupabaseSyllabusClient supabaseClient;
    private OpenRouterProperties openRouterProperties;
    private OpenRouterFleetService openRouterFleetService;
    private SyllabusRepositoryAdapter repositoryAdapter;
    private SyllabusServiceImpl syllabusService;
    private UtpPortalGatewayPort gatewayPortMock;
    private SpringDataSyllabusRepository springDataRepoMock;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();

        // 1. Supabase Client
        supabaseProperties = new SupabaseProperties();
        supabaseProperties.initFallbackKeys();
        supabaseClient = new SupabaseSyllabusClient(supabaseProperties, objectMapper);

        // 2. OpenRouter Fleet with Dynamic Model Selector
        openRouterProperties = new OpenRouterProperties();
        openRouterProperties.initFleetKeys();
        OpenRouterModelSelector modelSelector = new OpenRouterModelSelector(openRouterProperties, objectMapper);
        openRouterFleetService = new OpenRouterFleetService(openRouterProperties, modelSelector, objectMapper);

        // 3. Mocks for secondary dependencies
        springDataRepoMock = Mockito.mock(SpringDataSyllabusRepository.class);
        when(springDataRepoMock.searchSyllabus(anyString())).thenReturn(List.of());
        when(springDataRepoMock.findById(anyString())).thenReturn(Optional.empty());

        gatewayPortMock = Mockito.mock(UtpPortalGatewayPort.class);

        // 4. Adapter & Service
        repositoryAdapter = new SyllabusRepositoryAdapter(springDataRepoMock, supabaseClient, objectMapper);
        SyllabusParserEngine parserEngine = new SyllabusParserEngine();
        SyllabusMarkdownExporter markdownExporter = new SyllabusMarkdownExporter();

        syllabusService = new SyllabusServiceImpl(
                repositoryAdapter,
                gatewayPortMock,
                parserEngine,
                openRouterFleetService,
                markdownExporter
        );
    }

    @Test
    @DisplayName("Cache Hit: Curso existente en Supabase DB se retorna inmediatamente de la base de datos")
    void testCacheHitFromSupabase() {
        String code = "100000TEST_CACHE";
        supabaseClient.upsert(Syllabus.builder()
                .id(code)
                .courseCode(code)
                .courseName("SERVICIOS CLOUD EN CACHE")
                .formula("(0.3*PC1) + (0.7*EF)")
                .evaluations(List.of(
                        com.utp.horario.domain.model.SyllabusEvaluation.builder()
                                .type("PC1")
                                .description("Práctica Calificada 1")
                                .weightPercent(30)
                                .week(5)
                                .build()
                ))
                .weeklySchedule(List.of(
                        com.utp.horario.domain.model.SyllabusWeeklySession.builder()
                                .week(1)
                                .unit("Unidad 1")
                                .topic("Introducción a la Nube")
                                .activities("Laboratorio 1")
                                .build()
                ))
                .build());

        Syllabus s = syllabusService.getSyllabus(code, null, null, null);

        assertNotNull(s, "El sílabo debe ser retornado");
        assertEquals(code, s.getCourseCode());
        assertEquals("SERVICIOS CLOUD EN CACHE", s.getCourseName());
        assertNotNull(s.getFormula());
        assertNotNull(s.getEvaluations());
        assertFalse(s.getEvaluations().isEmpty(), "Debe contener evaluaciones guardadas en Supabase");
        assertNotNull(s.getWeeklySchedule());
        assertFalse(s.getWeeklySchedule().isEmpty(), "Debe contener el cronograma de Supabase");

        // Verificar que NO se intentó descargar el PDF de la UTP ya que estaba en caché de BD
        Mockito.verifyNoInteractions(gatewayPortMock);
    }

    @Test
    @DisplayName("Flota OpenRouter: 12 claves activas cargadas y disponibles para rotación")
    void testOpenRouterFleetLoaded() {
        assertNotNull(openRouterProperties.getKeys());
        assertEquals(12, openRouterProperties.getKeys().size(), "Deben haber 12 cuentas en la flota");
        assertTrue(openRouterProperties.isEnabled());
        assertNotNull(openRouterProperties.getPrimaryModel());
        assertFalse(openRouterProperties.getFallbackModels().isEmpty());
    }

    @Test
    @DisplayName("Extracción con IA + Persistencia en Supabase: Flujo completo Cache-Aside")
    void testAiParsingAndPersistenceFlow() {
        String testCourseCode = "100000TEST_ROBOTICS";
        String samplePdfText = """
                1. INFORMACIÓN GENERAL
                Curso: ROBÓTICA INDUSTRIAL
                Código: 100000TEST_ROBOTICS
                Créditos: 4
                Modalidad: Presencial
                Horas: 4
                Carrera: Ingeniería Mecatrónica

                2. LOGRO DEL CURSO
                Al finalizar el curso, el estudiante programa brazos robóticos para manufactura avanzada.

                3. SISTEMA DE EVALUACIÓN
                Fórmula: (30%)PC1 + (30%)PC2 + (40%)PROY
                Evaluaciones:
                PC1: Práctica Calificada 1, Semana 5, Individual, 30%
                PC2: Práctica Calificada 2, Semana 11, Individual, 30%
                PROY: Proyecto Robótico, Semana 18, Grupal, 40%

                4. CRONOGRAMA DE ACTIVIDADES
                Semana 1: Cinemática directa de manipuladores. Actividades: Análisis matricial.
                Semana 5: Evaluación PC1. Actividades: Cinemática inversa y trayectorias.
                Semana 11: Evaluación PC2. Actividades: Control de servomotores y dinámica.
                Semana 18: Sustentación PROY. Actividades: Demostración con robot físico.
                """;

        // Simular que el portal UTP descarga este texto para el nuevo curso
        when(gatewayPortMock.fetchSyllabusPdfText(anyString(), anyString())).thenReturn(samplePdfText);

        // 1. Primera petición: Cache miss -> Parsea con IA o Fallback -> Guarda en Supabase DB
        Syllabus result = syllabusService.getSyllabus(testCourseCode, null, null, null);

        assertNotNull(result);
        assertEquals(testCourseCode, result.getCourseCode());
        assertNotNull(result.getFormula(), "Debe haber extraído la fórmula");
        assertNotNull(result.getEvaluations(), "Debe contener evaluaciones");
        assertFalse(result.getEvaluations().isEmpty());

        // 2. Segunda petición: Cache hit -> Debe obtenerse directamente de Supabase sin invocar gateway
        Optional<Syllabus> fromDb = supabaseClient.findByCourseCode(testCourseCode);
        assertTrue(fromDb.isPresent(), "El curso procesado debe estar ahora persistido en Supabase");
        assertEquals(testCourseCode, fromDb.get().getCourseCode());

        // Limpiar registro de prueba de Supabase
        cleanupTestCourse(testCourseCode);
    }

    private void cleanupTestCourse(String courseCode) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(supabaseProperties.getUrl() + "/rest/v1/" + supabaseProperties.getTable() + "?course_id=eq." + courseCode))
                    .header("apikey", supabaseProperties.getServiceRoleKey())
                    .header("Authorization", "Bearer " + supabaseProperties.getServiceRoleKey())
                    .DELETE()
                    .build();
            client.send(req, java.net.http.HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }
}
