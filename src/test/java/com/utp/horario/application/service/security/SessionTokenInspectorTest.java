package com.utp.horario.application.service.security;

import com.utp.horario.domain.exception.AuthenticationException;
import com.utp.horario.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de regresión del inspecionador de tokens de sesión.
 *
 * <p>Cubren los hallazgos de auditoría H-02 (token expirado aceptado como sesión vigente)
 * y C-01 (resolución de identidad aislada por estudiante).</p>
 */
class SessionTokenInspectorTest {

    @Test
    @DisplayName("Un token vigente expone los claims normalizados del estudiante")
    void validTokenExposesNormalizedClaims() {
        String token = TestTokens.validFor("U99999999");

        SessionTokenInspector.SessionClaims claims = SessionTokenInspector.inspect(token);

        assertNotNull(claims);
        assertEquals("U99999999", claims.studentCode());
        assertEquals("sub-u99999999", claims.userId());
        assertEquals("u99999999@utp.edu.pe", claims.email());
        assertTrue(claims.expiresAt() != null && claims.expiresAt().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("El prefijo 'Bearer ' se normaliza correctamente")
    void bearerPrefixIsNormalized() {
        String token = TestTokens.validFor("U99999999");

        SessionTokenInspector.SessionClaims claims = SessionTokenInspector.inspect("Bearer " + token);

        assertEquals("U99999999", claims.studentCode());
        assertEquals(token, SessionTokenInspector.normalizeToken("Bearer " + token));
    }

    @Test
    @DisplayName("Un token expirado se rechaza con AuthenticationException (HTTP 401)")
    void expiredTokenIsRejected() {
        String expired = TestTokens.expiredFor("U99999999");

        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> SessionTokenInspector.inspect(expired));

        assertTrue(ex.getMessage().toLowerCase().contains("expirado"), ex.getMessage());
    }

    @Test
    @DisplayName("Un token ausente o malformado se rechaza con AuthenticationException (HTTP 401)")
    void missingOrMalformedTokenIsRejected() {
        assertThrows(AuthenticationException.class, () -> SessionTokenInspector.inspect(null));
        assertThrows(AuthenticationException.class, () -> SessionTokenInspector.inspect(""));
        assertThrows(AuthenticationException.class, () -> SessionTokenInspector.inspect("   "));
        assertThrows(AuthenticationException.class, () -> SessionTokenInspector.inspect("no-es-un-jwt"));
        assertThrows(AuthenticationException.class, () -> SessionTokenInspector.inspect("a.!!!not-base64!!!.c"));
    }

    @Test
    @DisplayName("tryInspect nunca propaga excepciones: devuelve vacío ante un token inválido")
    void tryInspectNeverThrows() {
        assertTrue(SessionTokenInspector.tryInspect(null).isEmpty());
        assertTrue(SessionTokenInspector.tryInspect("basura").isEmpty());
        assertTrue(SessionTokenInspector.tryInspect(TestTokens.expiredFor("U99999999")).isEmpty());
        assertFalse(SessionTokenInspector.tryInspect(TestTokens.validFor("U99999999")).isEmpty());
    }

    @Test
    @DisplayName("Cada token resuelve exclusivamente la identidad de su propio estudiante")
    void eachTokenResolvesOnlyItsOwnStudent() {
        SessionTokenInspector.SessionClaims a = SessionTokenInspector.inspect(TestTokens.validFor("U99999999"));
        SessionTokenInspector.SessionClaims b = SessionTokenInspector.inspect(TestTokens.validFor("U21200000"));

        assertEquals("U99999999", a.studentCode());
        assertEquals("U21200000", b.studentCode());
        assertFalse(a.studentCode().equals(b.studentCode()));
    }
}
