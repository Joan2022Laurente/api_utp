package com.utp.horario.support;

import com.utp.horario.infrastructure.security.JwksKeyProvider;
import com.utp.horario.infrastructure.security.JwtVerificationProperties;
import io.jsonwebtoken.Jwts;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utilidades de prueba para la verificacion de firma JWT.
 *
 * <p>Genera un par de claves RSA local, construye tokens firmados con el y expone un
 * {@link JwksKeyProvider} simulado que publica esa clave publica, de modo que las pruebas
 * no dependan de la red ni del SSO institucional.</p>
 */
public final class TestKeys {

    /** Identificador de clave de prueba. */
    public static final String KID = "clave-de-prueba-kid-001";

    /** Emisor esperado en las pruebas. */
    public static final String ISSUER = "https://sso.utp.edu.pe/auth/realms/Xpedition";

    private final KeyPair keyPair;

    public TestKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.keyPair = generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el par de claves RSA de prueba", e);
        }
    }

    public PublicKey publicKey() {
        return keyPair.getPublic();
    }

    public PrivateKey privateKey() {
        return keyPair.getPrivate();
    }

    /**
     * Construye un JWKS en el formato publicado por Keycloak para la clave de prueba.
     */
    public Map<String, Object> jwksDocument() {
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("kid", KID);
        key.put("kty", "RSA");
        key.put("alg", "RS256");
        key.put("use", "sig");
        key.put("n", base64UrlUnsigned(unsignedBytes(
                ((java.security.interfaces.RSAPublicKey) keyPair.getPublic()).getModulus().toByteArray())));
        key.put("e", base64UrlUnsigned(encodeExponent(keyPair.getPublic())));
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("keys", java.util.List.of(key));
        return document;
    }

    /**
     * Token firmado con la clave de prueba, con los claims minimos de una sesion UTP.
     */
    public String signedToken(String studentCode) {
        return signedToken(studentCode, ISSUER, 3600L);
    }

    /**
     * Token firmado con la clave de prueba, con emisor y caducidad parametrizables.
     */
    public String signedToken(String studentCode, String issuer, long ttlSeconds) {
        Date now = new Date();
        return Jwts.builder()
                .header().keyId(KID).and()
                .issuer(issuer)
                .subject("sub-" + studentCode.toLowerCase())
                .claim("preferred_username", studentCode)
                .claim("name", "ESTUDIANTE " + studentCode)
                .claim("email", studentCode.toLowerCase() + "@utp.edu.pe")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlSeconds * 1000L))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Token firmado con una clave distinta: la firma no cuadra con la clave publicada.
     */
    public String tokenSignedWithForeignKey(String studentCode) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair other = generator.generateKeyPair();
            Date now = new Date();
            return Jwts.builder()
                    .header().keyId(KID).and()
                    .issuer(ISSUER)
                    .subject("sub-" + studentCode.toLowerCase())
                    .claim("preferred_username", studentCode)
                    .issuedAt(now)
                    .expiration(new Date(now.getTime() + 3600_000L))
                    .signWith(other.getPrivate(), Jwts.SIG.RS256)
                    .compact();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Token con algoritmo HMAC: no debe aceptarse aunque se conozca la clave publica.
     */
    public String hmacToken(String studentCode) {
        Date now = new Date();
        return Jwts.builder()
                .header().keyId(KID).and()
                .issuer(ISSUER)
                .subject("sub-" + studentCode.toLowerCase())
                .claim("preferred_username", studentCode)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3600_000L))
                .signWith(new javax.crypto.spec.SecretKeySpec(
                        "clave-secreta-de-prueba-suficientemente-larga"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "HmacSHA256"), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Token con algoritmo {@code none}: nunca debe aceptarse.
     */
    public String unsignedToken(String studentCode) {
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\",\"kid\":\"" + KID + "\"}");
        String payload = base64Url("{\"iss\":\"" + ISSUER + "\",\"preferred_username\":\""
                + studentCode + "\",\"sub\":\"sub-" + studentCode.toLowerCase() + "\"}");
        return header + "." + payload + "." + "firma-falsa";
    }

    /**
     * Proveedor de claves simulado que publica la clave de prueba sin tocar la red.
     */
    public JwksKeyProvider provider(JwtVerificationProperties properties) {
        return new JwksKeyProvider(properties) {
            @Override
            protected Map<String, PublicKey> downloadKeys() {
                return Map.of(KID, publicKey());
            }
        };
    }

    /**
     * Proveedor de claves simulado que siempre falla, para simular la caida del SSO.
     */
    public JwksKeyProvider unavailableProvider(JwtVerificationProperties properties) {
        return new JwksKeyProvider(properties) {
            @Override
            protected Map<String, PublicKey> downloadKeys() throws java.io.IOException {
                throw new java.io.IOException("caida simulada del JWKS");
            }
        };
    }

    private static byte[] encodeExponent(PublicKey publicKey) {
        java.math.BigInteger exponent = ((java.security.interfaces.RSAPublicKey) publicKey).getPublicExponent();
        return unsignedBytes(exponent.toByteArray());
    }

    /**
     * Elimina los bytes iniciales de relleno para dejar el entero en big-endian sin signo.
     */
    private static byte[] unsignedBytes(byte[] bytes) {
        int offset = 0;
        while (offset < bytes.length - 1 && bytes[offset] == 0) {
            offset++;
        }
        byte[] trimmed = new byte[bytes.length - offset];
        System.arraycopy(bytes, offset, trimmed, 0, trimmed.length);
        return trimmed;
    }

    private static String base64UrlUnsigned(byte[] value) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String base64Url(String value) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
