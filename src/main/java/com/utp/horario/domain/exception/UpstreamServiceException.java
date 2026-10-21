package com.utp.horario.domain.exception;

/**
 * Excepción de dominio para indisponibilidad de un servicio externo (SSO Keycloak UTP,
 * Portal GraphQL, PAO Calendar, AWS S3 u OpenRouter).
 *
 * <p>Se traduce a HTTP 502 Bad Gateway en el
 * {@code com.utp.horario.presentation.exception.GlobalExceptionHandler},
 * diferenciando el fallo de integración de un fallo de validación del cliente (4xx).</p>
 */
public class UpstreamServiceException extends RuntimeException {

    public UpstreamServiceException(String message) {
        super(message);
    }

    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
