package com.utp.horario.infrastructure.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuracion de la verificacion criptografica de los tokens de sesion del SSO Keycloak UTP.
 *
 * <p>La pasarela recibe tokens firmados por el realm {@code Xpedition}. Sin verificar la
 * firma, un JWT manufactured a mano (con el {@code preferred_username} de la victima)
 * seria aceptado como sesion legitima. Esta clase permite activar la verificacion contra
 * el JWKS publico del realm con tres modos, de modo que el despliegue puedaarse de forma
 * progresiva y reversible sin recompilar:</p>
 *
 * <ul>
 *   <li>{@link Mode#OFF} — no se verifica nada (comportamiento heredado).</li>
 *   <li>{@link Mode#AUDIT} — se verifica y se registra el resultado, pero nunca se rechaza
 *       la peticion. Permite observar en produccion que el 100% de los tokens reales
 *       pasan la validacion antes de activar el rechazo.</li>
 *   <li>{@link Mode#ENFORCE} — un token con firma invalida se responde con
 *       {@code 401 Unauthorized} dentro del sobre {@code ApiResponse}.</li>
 * </ul>
 *
 * <p>El modo se controla con la variable de entorno {@code UTP_JWT_MODE}, de forma que
 * pueda cambiarse en caliente con {@code heroku config:set} sin redesplegar.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "utp.security.jwt")
public class JwtVerificationProperties {

    /**
     * Modos de operacion de la verificacion de firma.
     */
    public enum Mode {
        /** No se verifica la firma. */
        OFF,
        /** Se verifica y se registra, pero no se rechaza ninguna peticion. */
        AUDIT,
        /** Se verifica y un token invalido se responde con 401. */
        ENFORCE;

        /**
         * Interpreta el valor textual de la configuracion de forma tolerante.
         */
        public static Mode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return OFF;
            }
            String value = raw.trim().toUpperCase(java.util.Locale.ROOT);
            return switch (value) {
                case "AUDIT" -> AUDIT;
                case "ENFORCE" -> ENFORCE;
                default -> OFF;
            };
        }
    }

    /** Modo de operacion. */
    private Mode mode = Mode.OFF;

    /** Emisor esperado en el claim {@code iss} de los tokens del realm. */
    private String issuer = "https://sso.utp.edu.pe/auth/realms/Xpedition";

    /** Endpoint publico del JWKS del realm (clave publica de verificacion). */
    private String jwksUrl = "https://sso.utp.edu.pe/auth/realms/Xpedition/protocol/openid-connect/certs";

    /** Tiempo de vida de la cache de claves publicas, en segundos. */
    private long jwksCacheTtlSeconds = 21_600L;

    /** Tiempo maximo de espera de la descarga del JWKS, en segundos. */
    private long jwksTimeoutSeconds = 3L;

    /**
     * Si el JWKS no puede descargarse (caida de red), la peticion NO se rechaza: se registra
     * el incidente y se sigue adelante. Prioriza la disponibilidad ante una caida del SSO.
     */
    private boolean failOpen = true;

    /** Desfase de reloj tolerado al evaluar {@code exp}/{@code nbf}, en segundos. */
    private long clockSkewSeconds = 60L;

    /**
     * Rutas exentas de verificacion (relativas al context-path). Se excluyen el login y el
     * refresh porque gestionan sus propios credenciales, y los endpoints de documentacion.
     */
    private List<String> skipPaths = new ArrayList<>(List.of(
            "/auth/login",
            "/auth/refresh",
            "/actuator",
            "/v3/api-docs",
            "/swagger-ui",
            "/error"
    ));
}
