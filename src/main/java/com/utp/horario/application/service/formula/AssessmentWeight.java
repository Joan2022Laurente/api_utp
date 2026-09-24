package com.utp.horario.application.service.formula;

public record AssessmentWeight(String label, int percentage) {
    public AssessmentWeight {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("El identificador de evaluación no puede estar vacío.");
        }
        label = label.trim().toUpperCase();
    }
}
