package com.utp.horario.domain.port.out;

/**
 * Puerto de salida para la verificacion de la firma de un token de sesion.
 *
 * <p>Permite que la capa de aplicacion decida si puede confiar en un token presentado por
 * el cliente sin depender de detalles criptograficos ni del proveedor concreto de claves.
 * El adaptador de infraestructura (realm Keycloak UTP con firma RS256) es el que decide
 * en que modo opera: si la verificacion esta desactivada, el token se acepta tal cual,
 * tal como se comportaba la pasarela antes de introducir esta comprobacion.</p>
 */
public interface TokenSignatureVerifierPort {

    /**
     * Resultado de la comprobacion de un token.
     */
    enum Status {
        /** El token es legitimo (o la verificacion esta desactivada). */
        VALID,
        /** El token no supera la verificacion y debe rechazarse. */
        INVALID
    }

    /**
     * Comprueba si el token puede considerarse legitimo.
     *
     * @param token token de sesion presentado por el cliente (con o sin prefijo Bearer)
     * @return {@link Status#VALID} o {@link Status#INVALID}
     */
    Status check(String token);
}
