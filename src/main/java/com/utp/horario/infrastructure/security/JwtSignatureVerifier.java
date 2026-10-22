package com.utp.horario.infrastructure.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.port.out.TokenSignatureVerifierPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;

/**
 * Verificacion criptografica de los tokens de sesion emitidos por el SSO Keycloak UTP.
 *
 * <p>El token es un JWT firmado con RSA (RS256) por el realm {@code Xpedition}. La
 * verificacion comprueba, en este orden:</p>
 * <ol>
 *   <li>Que el token tenga forma de JWT de tres segmentos.</li>
 *   <li>Que el algoritmo declarado sea {@code RS256}. Se rechazan expresamente
 *       {@code none} y los algoritmos HMAC ({@code HS256}...): aceptarlos permitiria
 *       firmar un token usando la propia clave publica como secreto simetrico, un
 *       ataque clasico de confusion de algoritmos.</li>
 *   <li>Que exista en el JWKS la clave con el {@code kid} declarado y que la firma
 *       corresponda a esa clave.</li>
 *   <li>Que el emisor ({@code iss}) sea el del realm y que el token no este expirado
 *       ({@code exp}) ni sea todavia vigente ({@code nbf}); de las marcas temporales se
 *       encarga la libreria con una tolerancia de reloj configurable.</li>
 * </ol>
 *
 * <p>El resultado distingue entre <b>token invalido</b> (firma que no cuadra, algoritmo no
 * permitido, emisor distinto) y <b>token no verificable</b> (el JWKS no pudo descargarse
 * por un fallo de red). El segundo caso no es un ataque: es una caida del SSO, y su
 * tratamiento (rechazar o dejar pasar) lo decide la configuracion {@code fail-open}.</p>
 */
@Slf4j
@Component
public class JwtSignatureVerifier implements TokenSignatureVerifierPort {

    private static final String ALGORITHM_RS256 = "RS256";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtVerificationProperties properties;
    private final JwksKeyProvider keyProvider;
    private final TokenVerificationPolicy policy;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtSignatureVerifier(JwtVerificationProperties properties, JwksKeyProvider keyProvider) {
        this.properties = properties;
        this.keyProvider = keyProvider;
        this.policy = new TokenVerificationPolicy(properties);
    }

    /**
     * Aplica la politica de decision al resultado de una verificacion.
     *
     * <p>Es el punto unico donde se decide si un token invalido se rechaza, se registra o
     * se deja pasar, de modo que el filtro HTTP y la capa de aplicacion se comporten
     * exactamente igual.</p>
     */
    public TokenVerificationPolicy.Decision decide(Outcome outcome) {
        return policy.decide(outcome);
    }

    /**
     * Implementacion del puerto de dominio: indica si el token puede confiarse.
     */
    @Override
    public Status check(String token) {
        Outcome outcome = verify(token);
        return decide(outcome) == TokenVerificationPolicy.Decision.REJECT ? Status.INVALID : Status.VALID;
    }

    /**
     * Resultado de la verificacion de un token.
     *
     * @param valid      {@code true} si la firma y las restedricciones se cumplieron
     * @param verifiable {@code false} si no se pudo verificar por indisponibilidad del JWKS
     * @param reason     descripcion legible del resultado (motivo del rechazo)
     * @param kid        identificador de clave declarado en la cabecera del token
     * @param algorithm  algoritmo declarado en la cabecera del token
     * @param claims     claims verificados (vacio si la verificacion no fue satisfactoria)
     */
    public record Outcome(
            boolean valid,
            boolean verifiable,
            String reason,
            String kid,
            String algorithm,
            Map<String, Object> claims
    ) {
        public static Outcome ok(String kid, String algorithm, Map<String, Object> claims) {
            return new Outcome(true, true, "Firma verificada", kid, algorithm, claims);
        }

        public static Outcome invalid(String reason, String kid, String algorithm) {
            return new Outcome(false, true, reason, kid, algorithm, Map.of());
        }

        public static Outcome notVerifiable(String reason, String kid, String algorithm) {
            return new Outcome(false, false, reason, kid, algorithm, Map.of());
        }
    }

    /**
     * Verifica la firma y las restricciones del token.
     *
     * <p>Nunca lanza excepcion: el resultado describe siempre lo ocurrido para que el
     * llamador decida que hacer.</p>
     */
    public Outcome verify(String rawToken) {
        String token = normalizeToken(rawToken);
        if (token.isBlank()) {
            return Outcome.invalid("Token de autenticacion ausente", null, null);
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            return Outcome.invalid("El token no tiene la estructura de un JWT", null, null);
        }

        JsonNode header;
        try {
            header = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
        } catch (Exception e) {
            return Outcome.invalid("La cabecera del token no es un JSON valido", null, null);
        }
        if (header == null || !header.isObject()) {
            return Outcome.invalid("La cabecera del token no es un JSON valido", null, null);
        }

        String algorithm = header.path("alg").asText("");
        String kid = header.path("kid").asText("");

        if (algorithm.isBlank()) {
            return Outcome.invalid("El token no declara algoritmo", kid, null);
        }
        if (!ALGORITHM_RS256.equalsIgnoreCase(algorithm)) {
            // Rechazo explicito de 'none' y de los algoritmos HMAC: sin esta comprobacion
            // un atacante podria firmar con la clave publica usada como secreto HMAC.
            return Outcome.invalid("Algoritmo de firma no permitido: " + algorithm, kid, algorithm);
        }
        if (kid.isBlank()) {
            return Outcome.invalid("El token no declara identificador de clave (kid)", null, algorithm);
        }

        PublicKey publicKey;
        try {
            publicKey = keyProvider.resolve(kid);
        } catch (JwksKeyProvider.JwksUnavailableException e) {
            log.error("[JWT] No se pudo verificar la firma: el JWKS no esta disponible. {}", e.getMessage());
            return Outcome.notVerifiable("El JWKS del SSO no esta disponible: " + e.getMessage(), kid, algorithm);
        }

        if (publicKey == null) {
            return Outcome.invalid("La clave '" + kid + "' no esta publicada por el SSO", kid, algorithm);
        }

        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(properties.getIssuer())
                    .clockSkewSeconds(Math.max(0, properties.getClockSkewSeconds()))
                    .build()
                    .parseSignedClaims(token);

            Map<String, Object> claims = jws.getPayload();
            return Outcome.ok(kid, algorithm, claims == null ? Map.of() : claims);
        } catch (JwtException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return Outcome.invalid("Firma o condiciones del token invalidas: " + message, kid, algorithm);
        } catch (RuntimeException e) {
            return Outcome.invalid("No se pudo verificar el token: " + e.getClass().getSimpleName(), kid, algorithm);
        }
    }

    /**
     * Normaliza el encabezado {@code Authorization} y devuelve el token puro.
     */
    static String normalizeToken(String rawToken) {
        if (rawToken == null) {
            return "";
        }
        String token = rawToken.trim();
        if (token.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            token = token.substring(BEARER_PREFIX.length()).trim();
        }
        return token;
    }
}
