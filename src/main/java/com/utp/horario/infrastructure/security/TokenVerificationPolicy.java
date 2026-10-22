package com.utp.horario.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Politica de decision ante el resultado de la verificacion de un token.
 *
 * <p>Concentra en un unico sitio la regla que decide si un token invalido se rechaza, se
 * registra o se deja pasar. Asi el filtro HTTP y la capa de aplicacion se comportan de
 * forma identica, y el modo de operacion se puede cambiar en caliente sin tocar codigo.</p>
 *
 * <table border="1">
 *   <caption>Matriz de decision</caption>
 *   <tr><th>Modo</th><th>Token valido</th><th>Token con firma invalida</th><th>JWKS no disponible</th></tr>
 *   <tr><td>off</td><td>aceptado</td><td>aceptado</td><td>aceptado</td></tr>
 *   <tr><td>audit</td><td>aceptado</td><td>aceptado y registrado</td><td>aceptado y registrado</td></tr>
 *   <tr><td>enforce</td><td>aceptado</td><td>rechazado (401)</td><td>aceptado si hay fail-open, rechazado si no</td></tr>
 * </table>
 */
@Component
@RequiredArgsConstructor
public class TokenVerificationPolicy {

    private final JwtVerificationProperties properties;

    /**
     * Decision a aplicar.
     */
    public enum Decision {
        /** La peticion sigue su curso normal. */
        ACCEPT,
        /** La peticion sigue su curso, pero el incidente queda registrado. */
        LOG_ONLY,
        /** La peticion debe responderse con 401. */
        REJECT
    }

    /**
     * Decide que hacer con el resultado de la verificacion.
     */
    public Decision decide(JwtSignatureVerifier.Outcome outcome) {
        if (properties.getMode() == JwtVerificationProperties.Mode.OFF) {
            return Decision.ACCEPT;
        }
        if (outcome == null || outcome.valid()) {
            return Decision.ACCEPT;
        }
        if (!outcome.verifiable()) {
            // El JWKS no se pudo descargar: es una caida del SSO, no un ataque.
            return properties.isFailOpen() ? Decision.LOG_ONLY : Decision.REJECT;
        }
        if (properties.getMode() == JwtVerificationProperties.Mode.AUDIT) {
            return Decision.LOG_ONLY;
        }
        return Decision.REJECT;
    }
}
