package com.utp.horario.domain.exception;

/**
 * Excepción tipada lanzada cuando no es posible ejecutar la simulación de notas
 * por falta de fórmula oficial del curso. HTTP 400 Bad Request via GlobalExceptionHandler.
 */
public class GradeSimulationException extends RuntimeException {

    public GradeSimulationException(String message) {
        super(message);
    }

    public GradeSimulationException(String message, Throwable cause) {
        super(message, cause);
    }
}
