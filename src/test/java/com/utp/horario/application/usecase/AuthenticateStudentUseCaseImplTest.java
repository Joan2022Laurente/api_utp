package com.utp.horario.application.usecase;

import com.utp.horario.domain.exception.AuthenticationException;
import com.utp.horario.domain.exception.ResourceNotFoundException;
import com.utp.horario.domain.model.StudentProfile;
import com.utp.horario.domain.port.out.StudentRepositoryPort;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import com.utp.horario.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pruebas de regresión de la resolución de identidad en autenticación.
 *
 * <p>Cubren los hallazgos H-02 (token expirado aceptado como vigente) y H-04
 * (perfil inexistente reportado como error de validación en lugar de 404).</p>
 */
class AuthenticateStudentUseCaseImplTest {

    private StudentRepositoryPort studentRepository;
    private UtpPortalGatewayPort gateway;
    private AuthenticateStudentUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        studentRepository = Mockito.mock(StudentRepositoryPort.class);
        gateway = Mockito.mock(UtpPortalGatewayPort.class);
        useCase = new AuthenticateStudentUseCaseImpl(studentRepository, gateway);

        when(studentRepository.save(any(StudentProfile.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(studentRepository.findByStudentCode(any()))
                .thenReturn(Optional.empty());
        when(studentRepository.findById(any()))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Un token inválido se rechaza con AuthenticationException (HTTP 401)")
    void invalidTokenIsRejected() {
        assertThrows(AuthenticationException.class, () -> useCase.authenticateWithToken("basura"));
        assertThrows(AuthenticationException.class, () -> useCase.authenticateWithToken(null));
        assertThrows(AuthenticationException.class, () -> useCase.authenticateWithToken(""));
    }

    @Test
    @DisplayName("Un token expirado se rechaza con AuthenticationException (HTTP 401)")
    void expiredTokenIsRejected() {
        String expired = TestTokens.expiredFor("U99999999");

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> useCase.authenticateWithToken(expired));

        assertTrue(ex.getMessage().toLowerCase().contains("expirado"));
    }

    @Test
    @DisplayName("Un token vigente resuelve el perfil del alumno correspondiente")
    void validTokenResolvesProfile() {
        StudentProfile profile = useCase.authenticateWithToken(TestTokens.validFor("U99999999"));

        assertEquals("U99999999", profile.getStudentCode());
        assertEquals("sub-u99999999", profile.getId());
    }

    @Test
    @DisplayName("Un token manipulado con otro alumno resuelve ÚNICAMENTE a ese alumno")
    void forgedTokenResolvesOnlyItsOwnClaims() {
        StudentProfile profile = useCase.authenticateWithToken(TestTokens.validFor("U21200000"));

        assertEquals("U21200000", profile.getStudentCode());
        assertEquals("sub-u21200000", profile.getId());
    }

    @Test
    @DisplayName("Un perfil inexistente se reporta como ResourceNotFoundException (HTTP 404)")
    void missingProfileIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> useCase.getProfile("usr-inexistente"));
    }

    @Test
    @DisplayName("Las credenciales inválidas del portal se propagan sin envolverse")
    void upstreamAuthenticationFailurePropagates() {
        when(gateway.login("U99999999", "mala")).thenThrow(new AuthenticationException("Credenciales UTP inválidas."));

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> useCase.authenticateWithCredentials("U99999999", "mala"));

        assertEquals("Credenciales UTP inválidas.", ex.getMessage());
    }
}
