package com.utp.horario.application.usecase;

import com.utp.horario.application.service.SyllabusParserEngine;
import com.utp.horario.application.service.ai.OpenRouterFleetService;
import com.utp.horario.application.service.ai.SyllabusPdfToMarkdownSanitizer;
import com.utp.horario.application.service.export.SyllabusMarkdownExporter;
import com.utp.horario.application.service.validation.SyllabusDeterministicValidator;
import com.utp.horario.domain.exception.UpstreamServiceException;
import com.utp.horario.domain.model.Syllabus;
import com.utp.horario.domain.port.out.SyllabusRepositoryPort;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regla de honestidad de datos del sílabo.
 *
 * <p>Cuando la pasarela no consigue el documento oficial (sin caché válida y sin PDF), la
 * respuesta correcta es una <b>ausencia explícita</b> — {@link UpstreamServiceException},
 * traducida por el {@code GlobalExceptionHandler} a HTTP 502 con sobre `success:false` —
 * y nunca un objeto con créditos, modalidad u horas inventadas presentado como oficial.</p>
 *
 * <p>Todos los colaboradores externos son mocks: ninguna prueba toca OpenRouter,
 * el Portal UTP ni bases de datos reales (presupuesto de demo).</p>
 */
class SyllabusUnavailabilityTest {

    private static final String UNKNOWN_CODE = "999999XX99";

    private SyllabusRepositoryPort repository;
    private UtpPortalGatewayPort gateway;
    private OpenRouterFleetService fleet;
    private SyllabusServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(SyllabusRepositoryPort.class);
        gateway = Mockito.mock(UtpPortalGatewayPort.class);
        fleet = Mockito.mock(OpenRouterFleetService.class);

        when(repository.findByCourseCode(anyString())).thenReturn(Optional.empty());
        when(gateway.fetchSyllabusPdfText(anyString(), anyString())).thenReturn("");

        service = new SyllabusServiceImpl(repository, gateway, new SyllabusParserEngine(), fleet,
                new SyllabusMarkdownExporter(), new SyllabusPdfToMarkdownSanitizer(),
                new SyllabusDeterministicValidator());
    }

    @Test
    @DisplayName("Sin caché y sin PDF se responde ausencia explícita en vez de un sílabo fabricado")
    void unavailableSyllabusThrowsInsteadOfFabricating() {
        assertThrows(UpstreamServiceException.class, () -> service.getSyllabus(UNKNOWN_CODE, null, null, null));
    }

    @Test
    @DisplayName("La ausencia no consume IA y no persiste ninguna fila")
    void absenceNeverCallsAiOrPersists() {
        assertThrows(UpstreamServiceException.class, () -> service.getSyllabus(UNKNOWN_CODE, null, null, null));

        verify(fleet, never()).parseSyllabusWithAi(anyString(), anyString());
        verify(repository, never()).save(any(Syllabus.class));
    }
}
