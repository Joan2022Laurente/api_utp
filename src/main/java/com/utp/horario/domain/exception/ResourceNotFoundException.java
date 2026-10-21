package com.utp.horario.domain.exception;

/**
 * Excepción de dominio para recursos inexistentes (estudiante, curso, periodo,
 * sección o sílabo no disponibles en el repositorio).
 *
 * <p>Se traduce a HTTP 404 Not Found en el
 * {@code com.utp.horario.presentation.exception.GlobalExceptionHandler}.</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
