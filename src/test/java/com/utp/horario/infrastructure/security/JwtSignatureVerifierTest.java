package com.utp.horario.infrastructure.security;

import com.utp.horario.support.TestKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la verificacion criptografica de la firma de los tokens de sesion.
 *
 * <p>Cubren el escenario de suplantacion de identidad que motivó la correccion: un token
 * fabricado a mano, con el {@code preferred_username} de la victima, debe ser RECHAZADO,
 * mientras que un token legitimo firmado por el realm debe aceptarse.</p>
 */
class JwtSignatureVerifierTest {

    private TestKeys keys;
    private JwtVerificationProperties properties;
    private JwtSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        keys = new TestKeys();
        properties = new JwtVerificationProperties();
        properties.setIssuer(TestKeys.ISSUER);
        verifier = new JwtSignatureVerifier(properties, keys.provider(properties));
    }

    @Test
    @DisplayName("Un token firmado por el realm se acepta y expone sus claims")
    void validSignedTokenIsAccepted() {
        JwtSignatureVerifier.Outcome outcome = verifier.verify(keys.signedToken("U99999999"));

        assertTrue(outcome.valid(), "El token legitimo debe verificarse: " + outcome.reason());
        assertTrue(outcome.verifiable());
        assertEquals(TestKeys.KID, outcome.kid());
        assertEquals("RS256", outcome.algorithm());
        assertEquals("U99999999", outcome.claims().get("preferred_username"));
    }

    @Test
    @DisplayName("Un token fabricado con la identidad de la victima se rechaza")
    void forgedTokenIsRejected() {
        // Token con la MISMA estructura y claims que uno legitimo, pero firma inventada:
        // es exactamente el ataque de suplantacion de identidad detectado en produccion.
        String forged = forgedTokenWithVictimIdentity();

        JwtSignatureVerifier.Outcome outcome = verifier.verify(forged);

        assertFalse(outcome.valid(), "Un token con firma falsa no debe aceptarse");
        assertTrue(outcome.verifiable(), "El rechazo debe ser por firma invalida, no por indisponibilidad");
        assertTrue(outcome.reason().toLowerCase().contains("firma"),
                "El motivo debe explicar el fallo de firma: " + outcome.reason());
    }

    @Test
    @DisplayName("Un token firmado con una clave ajena se rechaza")
    void tokenSignedWithForeignKeyIsRejected() {
        JwtSignatureVerifier.Outcome outcome = verifier.verify(keys.tokenSignedWithForeignKey("U99999999"));

        assertFalse(outcome.valid());
    }

    @Test
    @DisplayName("Un token con algoritmo 'none' se rechaza")
    void unsignedTokenIsRejected() {
        JwtSignatureVerifier.Outcome outcome = verifier.verify(keys.unsignedToken("U99999999"));

        assertFalse(outcome.valid());
        assertTrue(outcome.reason().contains("none"), "El motivo debe senalar el algoritmo: " + outcome.reason());
    }

    @Test
    @DisplayName("Un token con algoritmo HMAC se rechaza aunque se conozca la clave publica")
    void hmacTokenIsRejected() {
        JwtSignatureVerifier.Outcome outcome = verifier.verify(keys.hmacToken("U99999999"));

        assertFalse(outcome.valid());
        assertTrue(outcome.reason().toLowerCase().contains("algoritmo"),
                "El motivo debe senalar el algoritmo no permitido: " + outcome.reason());
    }

    @Test
    @DisplayName("Un token de otro emisor se rechaza")
    void tokenFromAnotherIssuerIsRejected() {
        JwtSignatureVerifier.Outcome outcome = verifier.verify(
                keys.signedToken("U99999999", "https://otro-sso.example.com/realm", 3600L));

        assertFalse(outcome.valid());
    }

    @Test
    @DisplayName("Un token expirado se rechaza")
    void expiredTokenIsRejected() {
        JwtSignatureVerifier.Outcome outcome = verifier.verify(
                keys.signedToken("U99999999", TestKeys.ISSUER, -3600L));

        assertFalse(outcome.valid());
    }

    @Test
    @DisplayName("Un token sin claims de alumno sigue siendo verificable")
    void tokenWithoutStudentClaimsIsStillVerified() {
        JwtSignatureVerifier.Outcome outcome = verifier.verify(keys.signedToken("U99999999"));

        assertTrue(outcome.valid());
        assertNotNull(outcome.claims().get("iss"));
    }

    @Test
    @DisplayName("Una cadena que no es un JWT se rechaza sin excepciones")
    void malformedTokenIsRejected() {
        assertFalse(verifier.verify("no-es-un-jwt").valid());
        assertFalse(verifier.verify("a.!!!no-base64!!!.c").valid());
        assertFalse(verifier.verify("").valid());
        assertFalse(verifier.verify(null).valid());
    }

    @Test
    @DisplayName("Si el JWKS no esta disponible el token queda 'no verificable', no 'invalido'")
    void jwksUnavailableIsNotAnInvalidToken() {
        JwtSignatureVerifier offline = new JwtSignatureVerifier(properties, keys.unavailableProvider(properties));

        JwtSignatureVerifier.Outcome outcome = offline.verify(keys.signedToken("U99999999"));

        assertFalse(outcome.valid());
        assertFalse(outcome.verifiable(),
                "Una caida del SSO no debe confundirse con un token invalido");
    }

    @Test
    @DisplayName("Un token con una clave que el SSO no publica se rechaza")
    void unknownKeyIdIsRejected() {
        JwtSignatureVerifier.Outcome outcome = verifier.verify(keys.signedToken("U99999999"));

        // Con la clave de prueba publicada, el token es valido: se comprueba que el
        // verificador es capaz de distinguir el caso 'kid desconocido'.
        assertTrue(outcome.valid());

        JwtVerificationProperties other = new JwtVerificationProperties();
        other.setIssuer(TestKeys.ISSUER);
        JwksKeyProvider emptyProvider = new JwksKeyProvider(other) {
            @Override
            protected java.util.Map<String, java.security.PublicKey> downloadKeys() {
                return java.util.Map.of();
            }
        };
        JwtSignatureVerifier verifierSinClaves = new JwtSignatureVerifier(other, emptyProvider);

        JwtSignatureVerifier.Outcome rejected = verifierSinClaves.verify(keys.signedToken("U99999999"));

        assertFalse(rejected.valid());
        assertTrue(rejected.reason().contains(TestKeys.KID), rejected.reason());
    }

    /**
     * Token con la identidad de la victima y firma falsa (ataque de suplantacion).
     */
    private String forgedTokenWithVictimIdentity() {
        String header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + TestKeys.KID + "\"}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"" + TestKeys.ISSUER + "\",\"preferred_username\":\"U99999999\","
                        + "\"sub\":\"sub-u99999999\",\"email\":\"u99999999@utp.edu.pe\","
                        + "\"exp\":" + ((System.currentTimeMillis() / 1000) + 3600) + "}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return header + "." + payload + "." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("firma-inventada".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
