package com.utp.horario.domain.exception;

/**
 * Excepción de dominio para fallos de autenticación/autorización de sesión
 * (credenciales inválidas, token ausente, token expirado o token manipulado).
 *
 * <p>Se traduce a HTTP 401 Unauthorized en el
 * {@code com.utp.horario.presentation.exception.GlobalExceptionHandler}.</p>
 */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
