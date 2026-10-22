package com.utp.horario.application.usecase;

import com.utp.horario.application.service.SyllabusParserEngine;
import com.utp.horario.application.service.ai.OpenRouterFleetService;
import com.utp.horario.application.service.ai.SyllabusPdfToMarkdownSanitizer;
import com.utp.horario.application.service.export.SyllabusMarkdownExporter;
import com.utp.horario.application.service.validation.SyllabusDeterministicValidator;
import com.utp.horario.domain.model.CourseSummaryData;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.model.SyllabusEvaluation;
import com.utp.horario.domain.model.SyllabusWeeklySession;
import com.utp.horario.domain.port.out.SyllabusRepositoryPort;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas de regresion de la resolucion de la cache de silabos por clave alternativa.
 *
 * <p>La cache esta indexada por codigo de catalogo, pero los clientes consultan por
 * nombre de curso, por seccion numerica del Portal o por UUID de seccion de PAO. Sin
 * resolver esas equivalencias, cada peticion cae en el cold path (34 s) y Heroku devuelve
 * HTTP 503 al cortar la conexion a los 30 s.</p>
 */
class SyllabusServiceImplCacheResolutionTest {

    private static final String TOKEN = "fake.jwt.token";
    private static final String CATALOG_CODE = "100000SI82";
    private static final String COURSE_NAME = "FORMACION PARA LA INVESTIGACION - SISTEMAS";
    private static final String PORTAL_SECTION = "56357";
    private static final String PAO_SECTION_UUID = "2421dd0b-b5ee-5193-8190-902ec8403745";

    private SyllabusRepositoryPort repository;
    private UtpPortalGatewayPort gateway;
    private OpenRouterFleetService fleet;
    private SyllabusServiceImpl service;
    private final Map<String, Optional<Syllabus>> cache = new HashMap<>();

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(SyllabusRepositoryPort.class);
        gateway = Mockito.mock(UtpPortalGatewayPort.class);
        fleet = Mockito.mock(OpenRouterFleetService.class);

        cache.clear();
        when(repository.findByCourseCode(anyString()))
                .thenAnswer(inv -> cache.getOrDefault(inv.getArgument(0), Optional.empty()));
        when(repository.save(any(Syllabus.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new SyllabusServiceImpl(repository, gateway, new SyllabusParserEngine(), fleet,
                new SyllabusMarkdownExporter(), new SyllabusPdfToMarkdownSanitizer(),
                new SyllabusDeterministicValidator());
    }

    private Syllabus cachedSyllabus() {
        return Syllabus.builder()
                .id(CATALOG_CODE).courseCode(CATALOG_CODE).courseName(COURSE_NAME)
                .formula("(10%)ATI1 + (20%)ATI2 + (20%)ATI3 + (20%)PA + (30%)TI")
                .evaluations(List.of(
                        SyllabusEvaluation.builder().id("ATI1").type("ATI1").description("Avance 1").week(4).weightPercent(10).build(),
                        SyllabusEvaluation.builder().id("ATI2").type("ATI2").description("Avance 2").week(8).weightPercent(20).build(),
                        SyllabusEvaluation.builder().id("ATI3").type("ATI3").description("Avance 3").week(13).weightPercent(20).build(),
                        SyllabusEvaluation.builder().id("PA").type("PA").description("Participacion").week(17).weightPercent(20).build(),
                        SyllabusEvaluation.builder().id("TI").type("TI").description("Trabajo").week(18).weightPercent(30).build()))
                .weeklySchedule(List.of(
                        SyllabusWeeklySession.builder().week(1).unit("Unidad 1").topic("Lineamientos generales").build(),
                        SyllabusWeeklySession.builder().week(2).unit("Unidad 1").topic("Fuentes de informacion").build(),
                        SyllabusWeeklySession.builder().week(3).unit("Unidad 1").topic("Lineas de investigacion").build(),
                        SyllabusWeeklySession.builder().week(4).unit("Unidad 1").topic("Introduccion de la RSL").build()))
                .build();
    }

    @Test
    @DisplayName("Un sectionId UUID de PAO resuelve el codigo de catalogo y localiza la cache")
    void paoSectionUuidResolvesCatalogCode() {
        cache.put(CATALOG_CODE, Optional.of(cachedSyllabus()));
        when(gateway.resolveCourseCode(Mockito.eq(PAO_SECTION_UUID), any(), anyString())).thenReturn(CATALOG_CODE);

        Syllabus result = service.getSyllabus(COURSE_NAME, PAO_SECTION_UUID, null, TOKEN);

        assertNotNull(result);
        assertEquals(CATALOG_CODE, result.getCourseCode());
        // No debe dispararse el cold path (ni descarga de PDF ni IA).
        verify(fleet, never()).parseSyllabusWithAi(anyString(), anyString());
    }

    @Test
    @DisplayName("Un sectionId numerico del Portal resuelve el codigo de catalogo y localiza la cache")
    void portalNumericSectionResolvesCatalogCode() {
        cache.put(CATALOG_CODE, Optional.of(cachedSyllabus()));
        when(gateway.fetchCourseSummary(anyString(), anyString())).thenReturn(CourseSummaryData.builder()
                .periodId("2263")
                .courses(List.of(CourseSummaryData.CourseGradeItem.builder()
                        .courseCode(CATALOG_CODE).courseName(COURSE_NAME).section(PORTAL_SECTION).build()))
                .build());

        Syllabus result = service.getSyllabus(COURSE_NAME, PORTAL_SECTION, null, TOKEN);

        assertNotNull(result);
        assertEquals(CATALOG_CODE, result.getCourseCode());
        verify(fleet, never()).parseSyllabusWithAi(anyString(), anyString());
    }

    @Test
    @DisplayName("Sin sectionId se consulta directamente por la clave solicitada (nombre o codigo)")
    void directLookupByRequestedKey() {
        cache.put(COURSE_NAME, Optional.of(cachedSyllabus()));

        Syllabus result = service.getSyllabus(COURSE_NAME, null, null, TOKEN);

        assertNotNull(result);
        assertEquals(CATALOG_CODE, result.getCourseCode());
        verify(gateway, never()).resolveCourseCode(anyString(), any(), anyString());
    }
}
