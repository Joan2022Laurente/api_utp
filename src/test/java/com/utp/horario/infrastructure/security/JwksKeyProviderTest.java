package com.utp.horario.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.utp.horario.support.TestKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.security.Signature;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del proveedor de claves publicas del JWKS.
 *
 * <p>Se levanta un servidor HTTP local que imita el endpoint publico del realm Keycloak,
 * de modo que se ejerciten la descarga, la conversion del JWKS a clave publica RSA, la
 * cache con TTL y la recarga por rotacion de claves sin depender de la red externa.</p>
 */
class JwksKeyProviderTest {

    private HttpServer server;
    private final TestKeys keys = new TestKeys();
    private final AtomicInteger requests = new AtomicInteger();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/certs", exchange -> {
            requests.incrementAndGet();
            byte[] body = objectMapper.writeValueAsBytes(keys.jwksDocument());
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private JwtVerificationProperties properties() {
        JwtVerificationProperties properties = new JwtVerificationProperties();
        properties.setIssuer(TestKeys.ISSUER);
        properties.setJwksUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/certs");
        properties.setJwksCacheTtlSeconds(600);
        properties.setJwksTimeoutSeconds(3);
        return properties;
    }

    @Test
    @DisplayName("El JWKS se convierte en una clave publica RSA utilizable")
    void downloadsAndConvertsTheKey() throws Exception {
        JwksKeyProvider provider = new JwksKeyProvider(properties());

        var key = provider.resolve(TestKeys.KID);

        assertNotNull(key, "La clave publicada debe resolverse");
        // La clave reconstruida debe validar una firma hecha con la clave privada.
        String token = keys.signedToken("U99999999");
        String[] parts = token.split("\\.");
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(key);
        signature.update((parts[0] + "." + parts[1]).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(signature.verify(java.util.Base64.getUrlDecoder().decode(parts[2])),
                "La clave publica reconstruida debe validar la firma del token");
    }

    @Test
    @DisplayName("La clave se cachea y no se vuelve a descargar en cada peticion")
    void cachesTheKeys() throws Exception {
        JwksKeyProvider provider = new JwksKeyProvider(properties());

        provider.resolve(TestKeys.KID);
        provider.resolve(TestKeys.KID);
        provider.resolve(TestKeys.KID);

        assertEquals(1, requests.get(), "El JWKS debe descargarse una sola vez mientras la cache es valida");
        assertFalse(provider.isCacheStale());
    }

    @Test
    @DisplayName("Un kid desconocido provoca una recarga (rotacion de claves de Keycloak)")
    void unknownKidTriggersARefresh() throws Exception {
        JwksKeyProvider provider = new JwksKeyProvider(properties());

        provider.resolve(TestKeys.KID);
        assertNull(provider.resolve("clave-inexistente"));
        assertEquals(2, requests.get(), "Un kid desconocido debe forzar una recarga del JWKS");
    }

    @Test
    @DisplayName("Si el JWKS no responde se propaga un error de indisponibilidad, no un token invalido")
    void unreachableJwksIsReportedAsUnavailable() {
        JwtVerificationProperties properties = properties();
        properties.setJwksUrl("http://127.0.0.1:1/certs");
        JwksKeyProvider provider = new JwksKeyProvider(properties);

        assertThrows(JwksKeyProvider.JwksUnavailableException.class, () -> provider.resolve(TestKeys.KID));
    }

    @Test
    @DisplayName("Un kid vacio no dispara ninguna descarga")
    void blankKidDoesNotTriggerDownload() throws Exception {
        JwksKeyProvider provider = new JwksKeyProvider(properties());

        assertNull(provider.resolve(null));
        assertNull(provider.resolve("  "));
        assertEquals(0, requests.get());
    }

    @Test
    @DisplayName("La cache caduca pasado el TTL configurado")
    void cacheExpiresAfterTtl() throws Exception {
        JwtVerificationProperties properties = properties();
        properties.setJwksCacheTtlSeconds(0);
        JwksKeyProvider provider = new JwksKeyProvider(properties);

        provider.resolve(TestKeys.KID);
        assertTrue(provider.isCacheStale(), "Con TTL 0 la cache debe considerarse caduca");

        provider.resolve(TestKeys.KID);
        assertEquals(2, requests.get(), "Una cache caducada debe volver a descargarse");
    }
}
