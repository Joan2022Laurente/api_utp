package com.utp.horario.infrastructure.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Proveedor de claves publicas del realm Keycloak a partir del JWKS publico.
 *
 * <p>El realm {@code Xpedition} publica sus claves de firma en un endpoint publico y sin
 * autenticacion. Este componente lo descarga, lo cachea en memoria y resuelve la clave
 * publica correspondiente al {@code kid} (identificador de clave) de cada token.</p>
 *
 * <p>Detalles de operacion relevantes:</p>
 * <ul>
 *   <li><b>Cache con TTL</b>: el JWKS se descarga como maximo una vez por TTL (6 h por
 *       defecto) para no golpear al SSO en cada peticion.</li>
 *   <li><b>Rotacion de claves</b>: si el {@code kid} de un token no esta en la cache, se
 *       fuerza una recarga (Keycloak rota claves periodicamente) y se vuelve a buscar.</li>
 *   <li><b>Anti-hammering</b>: si la descarga falla, no se reintenta en cada peticion
 *       durante la ventana de enfriamiento.</li>
 *   <li><b>Disponibilidad ante caida</b>: un fallo de red se propaga como
 *       {@link JwksUnavailableException} para que el llamador decida (fail-open) en lugar
 *       de invalidar sesiones legitimas.</li>
 * </ul>
 */
@Slf4j
@Component
public class JwksKeyProvider {

    /** Ventana de enfriamiento tras un fallo de descarga, en segundos. */
    private static final long FAILURE_COOLDOWN_SECONDS = 30L;

    private final JwtVerificationProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    private final Object refreshLock = new Object();
    private final Object altRefreshLock = new Object();

    private volatile Map<String, PublicKey> cachedKeys = Map.of();
    private volatile Instant cachedAt = Instant.EPOCH;
    private volatile Instant lastFailureAt = Instant.EPOCH;

    private volatile Map<String, PublicKey> altCachedKeys = Map.of();
    private volatile Instant altCachedAt = Instant.EPOCH;
    private volatile Instant altLastFailureAt = Instant.EPOCH;
    private volatile String altCachedUrl = "";

    @org.springframework.beans.factory.annotation.Autowired
    public JwksKeyProvider(JwtVerificationProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public JwksKeyProvider(JwtVerificationProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    /**
     * Resuelve la clave publica de verificacion de un token.
     *
     * <p>Se mantiene para compatibilidad: busca en el JWKS configurado y, si el
     * {@code kid} no aparece, intenta el realm institucional alternativo.</p>
     *
     * @param kid identificador de clave declarado en la cabecera del JWT
     * @return la clave publica, o {@code null} si el JWKS esta disponible pero no publica
     *         esa clave (token firmado con una clave ajena o revocada)
     * @throws JwksUnavailableException si el JWKS no pudo descargarse por un fallo de red
     */
    public PublicKey resolve(String kid) throws JwksUnavailableException {
        if (kid == null || kid.isBlank()) {
            return null;
        }
        PublicKey key = resolveFromPrimary(kid);
        if (key != null) {
            return key;
        }
        return resolveFromAlternativeUtpRealm(kid);
    }

    /**
     * Resuelve la clave publica del realm que declara el token. A diferencia de
     * {@link #resolve(String)}, no mezcla claves de ambos realms: el {@code iss} decide
     * que JWKS se consulta, de modo que una clave del realm {@code auth} no puede validar
     * un token que dice venir del realm {@code sso} (ni al reves).
     *
     * @param kid    identificador de clave declarado en la cabecera del JWT
     * @param issuer emisor declarado por el token (todavia no verificado)
     * @return la clave publica del realm del emisor, o {@code null} si no existe
     * @throws JwksUnavailableException si el JWKS del realm no pudo descargarse
     */
    public PublicKey resolveForIssuer(String kid, String issuer) throws JwksUnavailableException {
        if (kid == null || kid.isBlank() || issuer == null || issuer.isBlank()) {
            return null;
        }
        if (issuer.equalsIgnoreCase(properties.getIssuer())) {
            return resolveFromPrimary(kid);
        }
        String altUrl = alternativeUrlForIssuer(issuer);
        if (altUrl == null) {
            return null;
        }
        // Solo se consulta el realm alternativo si la configuracion apunta a un realm
        // institucional. Con un JWKS personalizado no se inventan endpoints externos.
        if (!isInstitutionalJwksUrl(properties.getJwksUrl())) {
            return null;
        }
        return resolveFromAlternativeRealm(kid, altUrl);
    }

    private PublicKey resolveFromPrimary(String kid) throws JwksUnavailableException {
        Map<String, PublicKey> keys = cachedKeys;
        if (isCacheStale()) {
            keys = refresh();
        }

        PublicKey key = keys.get(kid);
        if (key != null) {
            return key;
        }

        // El kid no esta en la cache: puede tratarse de una rotacion de claves de Keycloak.
        // Se fuerza una recarga y se busca de nuevo una unica vez.
        log.info("[JWKS] La clave '{}' no esta en la cache; recargando el JWKS por rotacion de claves.", kid);
        return refresh().get(kid);
    }

    private static final String AUTH_ISSUER = "https://auth.utp.edu.pe/realms/Xpedition";
    private static final String SSO_ISSUER = "https://sso.utp.edu.pe/auth/realms/Xpedition";
    private static final String AUTH_REALM_CERTS = AUTH_ISSUER + "/protocol/openid-connect/certs";
    private static final String SSO_REALM_CERTS = SSO_ISSUER + "/protocol/openid-connect/certs";

    private String alternativeUrlForIssuer(String issuer) {
        if (AUTH_ISSUER.equalsIgnoreCase(issuer)) {
            return AUTH_REALM_CERTS;
        }
        if (SSO_ISSUER.equalsIgnoreCase(issuer)) {
            return SSO_REALM_CERTS;
        }
        return null;
    }

    private boolean isInstitutionalJwksUrl(String url) {
        return url != null && (url.contains("auth.utp.edu.pe") || url.contains("sso.utp.edu.pe"));
    }

    private PublicKey resolveFromAlternativeUtpRealm(String kid) {
        String currentUrl = properties.getJwksUrl();
        if (currentUrl == null) {
            return null;
        }
        String altUrl = null;
        if (currentUrl.contains("auth.utp.edu.pe")) {
            altUrl = SSO_REALM_CERTS;
        } else if (currentUrl.contains("sso.utp.edu.pe")) {
            altUrl = AUTH_REALM_CERTS;
        }
        if (altUrl == null) {
            return null;
        }
        try {
            return resolveFromAlternativeRealm(kid, altUrl);
        } catch (JwksUnavailableException e) {
            log.warn("[JWKS] No se pudo consultar el realm alternativo {}: {}", altUrl, e.getMessage());
            return null;
        }
    }

    private PublicKey resolveFromAlternativeRealm(String kid, String altUrl) throws JwksUnavailableException {
        synchronized (altRefreshLock) {
            boolean sameUrl = altUrl.equals(altCachedUrl);
            if (sameUrl && !isAltCacheStale()) {
                PublicKey cached = altCachedKeys.get(kid);
                if (cached != null) {
                    return cached;
                }
            }
            if (sameUrl && altCachedKeys.isEmpty()
                    && altLastFailureAt.plusSeconds(FAILURE_COOLDOWN_SECONDS).isAfter(Instant.now())) {
                throw new JwksUnavailableException(
                        "El JWKS alternativo no esta disponible (ultimo intento fallido hace menos de "
                                + FAILURE_COOLDOWN_SECONDS + " s).");
            }
            try {
                log.info("[JWKS] Buscando clave '{}' en el realm institucional alternativo: {}", kid, altUrl);
                Map<String, PublicKey> keys = downloadKeys(altUrl);
                altCachedKeys = keys;
                altCachedAt = Instant.now();
                altCachedUrl = altUrl;
                altLastFailureAt = Instant.EPOCH;
                return keys.get(kid);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                altLastFailureAt = Instant.now();
                throw new JwksUnavailableException("La descarga del JWKS alternativo fue interrumpida.", e);
            } catch (IOException | RuntimeException e) {
                altLastFailureAt = Instant.now();
                throw new JwksUnavailableException("No se pudo descargar el JWKS alternativo: " + e.getMessage(), e);
            }
        }
    }

    private boolean isAltCacheStale() {
        long ttl = properties.getJwksCacheTtlSeconds();
        if (ttl <= 0) {
            return true;
        }
        return altCachedAt.plusSeconds(ttl).isBefore(Instant.now());
    }

    /**
     * Indica si la cache de claves publicas ha caducado.
     */
    public boolean isCacheStale() {
        long ttl = properties.getJwksCacheTtlSeconds();
        if (ttl <= 0) {
            return true;
        }
        return cachedAt.plusSeconds(ttl).isBefore(Instant.now());
    }

    /**
     * Fuerza la recarga del JWKS.
     */
    public Map<String, PublicKey> refresh() throws JwksUnavailableException {
        synchronized (refreshLock) {
            // Anti-hammering: si la ultima descarga fallo hace instantes, no se insiste.
            if (cachedKeys.isEmpty()
                    && lastFailureAt.plusSeconds(FAILURE_COOLDOWN_SECONDS).isAfter(Instant.now())) {
                throw new JwksUnavailableException(
                        "El JWKS no esta disponible (ultimo intento fallido hace menos de "
                                + FAILURE_COOLDOWN_SECONDS + " s).");
            }
            try {
                Map<String, PublicKey> keys = downloadKeys();
                cachedKeys = keys;
                cachedAt = Instant.now();
                lastFailureAt = Instant.EPOCH;
                log.info("[JWKS] Claves publicas cargadas: {} (kid(s): {}).",
                        keys.size(), keys.keySet());
                return keys;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                lastFailureAt = Instant.now();
                throw new JwksUnavailableException("La descarga del JWKS fue interrumpida.", e);
            } catch (IOException | RuntimeException e) {
                lastFailureAt = Instant.now();
                throw new JwksUnavailableException("No se pudo descargar el JWKS: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Descarga y convierte el JWKS en un mapa {@code kid -> clave publica}.
     *
     * <p>Separado del resto del flujo para que las pruebas puedan sustituir la red por un
     * conjunto de claves deterministas.</p>
     */
    protected Map<String, PublicKey> downloadKeys() throws IOException, InterruptedException {
        return downloadKeys(properties.getJwksUrl());
    }

    private Map<String, PublicKey> downloadKeys(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(1, properties.getJwksTimeoutSeconds())))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("El JWKS respondio HTTP " + response.statusCode());
        }

        return parseKeysNode(response.body());
    }

    private Map<String, PublicKey> parseKeysNode(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode keysNode = root.path("keys");
        if (!keysNode.isArray()) {
            throw new IOException("El JWKS no contiene un array 'keys'.");
        }

        Map<String, PublicKey> result = new HashMap<>();
        for (JsonNode keyNode : keysNode) {
            String kty = keyNode.path("kty").asText("");
            if (!"RSA".equalsIgnoreCase(kty)) {
                continue;
            }
            String kid = keyNode.path("kid").asText("");
            String n = keyNode.path("n").asText("");
            String e = keyNode.path("e").asText("");
            if (kid.isBlank() || n.isBlank() || e.isBlank()) {
                continue;
            }
            try {
                result.put(kid, buildRsaPublicKey(n, e));
            } catch (Exception ex) {
                log.warn("[JWKS] La clave '{}' no se pudo convertir a clave publica RSA: {}", kid, ex.getMessage());
            }
        }
        return Map.copyOf(result);
    }

    /**
     * Construye una clave publica RSA a partir de los parametros {@code n} y {@code e} en
     * base64url, tal como los publica el JWKS.
     */
    static PublicKey buildRsaPublicKey(String modulus, String exponent) throws Exception {
        BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(modulus));
        BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(exponent));
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
    }

    /**
     * Excepcion interna: el JWKS no pudo obtenerse por un fallo de red o de servicio.
     */
    public static class JwksUnavailableException extends Exception {
        public JwksUnavailableException(String message) {
            super(message);
        }

        public JwksUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
