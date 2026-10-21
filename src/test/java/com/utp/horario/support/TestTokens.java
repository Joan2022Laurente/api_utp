package com.utp.horario.support;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fábrica de tokens JWT de prueba para el realm Xpedition de Keycloak UTP.
 *
 * <p>Genera tokens con la misma estructura de claims que emite el SSO institucional
 * ({@code preferred_username}, {@code sub}, {@code name}, {@code email}, {@code exp}),
 * de modo que las pruebas puedan ejercitar el aislamiento de identidad y la validación
 * de vigencia sin depender del SSO real.</p>
 */
public final class TestTokens {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private TestTokens() {
    }

    /** Token vigente para el alumno indicado. */
    public static String validFor(String studentCode) {
        return build(studentCode, Instant.now().plusSeconds(3600).getEpochSecond(), null);
    }

    /** Token expirado para el alumno indicado. */
    public static String expiredFor(String studentCode) {
        return build(studentCode, Instant.now().minusSeconds(3600).getEpochSecond(), null);
    }

    public static String build(String studentCode, long expEpochSeconds, Map<String, Object> extraClaims) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("preferred_username", studentCode);
        payload.put("sub", "sub-" + studentCode.toLowerCase());
        payload.put("name", "ESTUDIANTE " + studentCode);
        payload.put("email", studentCode.toLowerCase() + "@utp.edu.pe");
        payload.put("iss", "https://sso.utp.edu.pe/auth/realms/Xpedition");
        payload.put("iat", Instant.now().getEpochSecond());
        payload.put("exp", expEpochSeconds);
        if (extraClaims != null) {
            payload.putAll(extraClaims);
        }

        String header = encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String body = encode(writeJson(payload));
        return header + "." + body + ".c2lnbmF0dXJl";
    }

    private static String writeJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el payload JWT de prueba", e);
        }
    }

    private static String encode(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
