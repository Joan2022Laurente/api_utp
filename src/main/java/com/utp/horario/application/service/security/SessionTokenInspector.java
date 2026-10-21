package com.utp.horario.application.service.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.exception.AuthenticationException;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Inspección de tokens de sesión emitidos por el SSO Keycloak UTP (realm Xpedition).
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Normalizar el encabezado {@code Authorization: Bearer &lt;token&gt;}.</li>
 *   <li>Validar la <b>vigencia temporal</b> del token ({@code exp} / {@code nbf} / {@code iat}),
 *       de modo que un token expirado nunca sea aceptado como sesión vigente.</li>
 *   <li>Exponer los claims normalizados que consume el núcleo de aplicación.</li>
 * </ul>
 *
 * <p><b>Aislamiento de identidad:</b> esta clase es <b>stateless</b>. No mantiene ningún
 * token en memoria compartida, por lo que es imposible que una petición sin token propio
 * tome prestado el token de otro estudiante. La resolución de sesiones vive en
 * {@code UtpPortalGatewayAdapter}, indexada por código de alumno.</p>
 *
 * <p>La validación criptográfica de la firma (RS256 contra el JWKS de Keycloak) queda
 * fuera del alcance stateless de este inspector y se reporta como remediación
 * pendiente en la matriz de auditoría (H-03).</p>
 */
public final class SessionTokenInspector {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BEARER_PREFIX = "Bearer ";

    private SessionTokenInspector() {
    }

    /**
     * Claims normalizados de un token de sesión válido.
     *
     * @param studentCode código de alumno en mayúsculas (puede estar vacío si el token no lo expone)
     * @param userId      identificador estable del usuario en el SSO
     * @param fullName    nombre completo del estudiante
     * @param email       correo institucional
     * @param career      carrera/programa
     * @param campus      sede/campus
     * @param cycle       ciclo académico
     * @param expiresAt   instante de expiración declarado por el emisor (puede ser nulo)
     */
    public record SessionClaims(
            String studentCode,
            String userId,
            String fullName,
            String email,
            String career,
            String campus,
            int cycle,
            Instant expiresAt
    ) {
    }

    /**
     * Normaliza el encabezado Authorization y extrae el token puro.
     */
    public static String normalizeToken(String rawToken) {
        if (rawToken == null) {
            return "";
        }
        String token = rawToken.trim();
        if (token.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            token = token.substring(BEARER_PREFIX.length()).trim();
        }
        return token;
    }

    /**
     * Inspecciona el token validando su vigencia temporal.
     *
     * @throws AuthenticationException si el token está ausente, malformado, expirado o todavía no vigente.
     */
    public static SessionClaims inspect(String rawToken) {
        String token = normalizeToken(rawToken);
        if (token.isBlank()) {
            throw new AuthenticationException("Token de autenticación UTP requerido.");
        }
        if (!token.contains(".")) {
            throw new AuthenticationException("Token de autenticación UTP inválido o expirado.");
        }

        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new AuthenticationException("Token de autenticación UTP inválido o expirado.");
        }

        JsonNode payload;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            payload = MAPPER.readTree(decoded);
        } catch (Exception e) {
            throw new AuthenticationException("Token de autenticación UTP inválido o expirado.", e);
        }
        if (payload == null || !payload.isObject()) {
            throw new AuthenticationException("Token de autenticación UTP inválido o expirado.");
        }

        Instant now = Instant.now();

        Long exp = readEpochSecond(payload, "exp");
        if (exp != null && Instant.ofEpochSecond(exp).isBefore(now)) {
            throw new AuthenticationException("El token de sesión ha expirado. Renueve la sesión con POST /auth/refresh.");
        }

        Long nbf = readEpochSecond(payload, "nbf");
        if (nbf != null && Instant.ofEpochSecond(nbf).isAfter(now)) {
            throw new AuthenticationException("El token de sesión todavía no es válido.");
        }

        Long iat = readEpochSecond(payload, "iat");
        if (iat != null && Instant.ofEpochSecond(iat).isAfter(now.plusSeconds(300))) {
            throw new AuthenticationException("El token de sesión presenta una marca temporal inconsistente.");
        }

        String studentCode = text(payload, "preferred_username", "username");
        studentCode = studentCode == null ? "" : studentCode.trim().toUpperCase();

        String userId = text(payload, "userId", "user_id", "paoUserId", "sub");
        if (userId == null || userId.isBlank()) {
            userId = studentCode.isBlank() ? "usr-student" : studentCode;
        }

        String fullName = text(payload, "name", "full_name");
        if (fullName == null || fullName.isBlank()) {
            fullName = text(payload, "given_name");
        }
        if (fullName == null || fullName.isBlank()) {
            fullName = studentCode;
        }

        String email = text(payload, "email");
        if (email == null || email.isBlank()) {
            email = studentCode.isBlank() ? "" : studentCode.toLowerCase() + "@utp.edu.pe";
        }

        String career = text(payload, "career", "carrera");
        String campus = text(payload, "campus", "campusDesc", "sede");
        int cycle = readInt(payload, "cycle", "ciclo", 1);

        return new SessionClaims(studentCode, userId, fullName, email, career, campus, cycle,
                exp != null ? Instant.ofEpochSecond(exp) : null);
    }

    /**
     * Variante no Lanzadora: devuelve {@link Optional#empty()} si el token es inválido o expirado.
     * Útil para derivar claves de caché por estudiante sin propagar excepciones.
     */
    public static Optional<SessionClaims> tryInspect(String rawToken) {
        try {
            return Optional.of(inspect(rawToken));
        } catch (AuthenticationException e) {
            return Optional.empty();
        }
    }

    private static Long readEpochSecond(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asLong();
    }

    private static int readInt(JsonNode payload, String field, String altField, int fallback) {
        Long value = readEpochSecond(payload, field);
        if (value == null) {
            value = readEpochSecond(payload, altField);
        }
        return value != null ? value.intValue() : fallback;
    }

    private static String text(JsonNode payload, String... fields) {
        for (String field : fields) {
            JsonNode node = payload.get(field);
            if (node != null && node.isNull()) {
                continue;
            }
            if (node != null && node.isValueNode()) {
                String value = node.asText();
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return "";
    }
}
