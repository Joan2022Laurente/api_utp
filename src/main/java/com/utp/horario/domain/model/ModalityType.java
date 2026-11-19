package com.utp.horario.domain.model;

/** Modalidad institucional real de una asignatura, derivada del dashboard de UTP+ Class. */
public enum ModalityType {
    PRESENCIAL("P"),
    REMOTE_LIVE("R"),
    ASYNC_VIRTUAL("VT"),
    UNKNOWN("");

    private final String code;

    ModalityType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Normaliza los valores observados en los payloads (`P`, `R`, `VT`, `V`, `Virtual 24/7`). */
    public static ModalityType from(String raw) {
        if (raw == null || raw.isBlank()) return UNKNOWN;
        String value = raw.trim().toUpperCase();
        if (value.startsWith("VT") || value.contains("24/7") || value.startsWith("ASYNC")) {
            return ASYNC_VIRTUAL;
        }
        return switch (value) {
            case "P", "PRESENCIAL" -> PRESENCIAL;
            case "R", "REMOTO" -> REMOTE_LIVE;
            case "V", "VIRTUAL" -> ASYNC_VIRTUAL;
            default -> UNKNOWN;
        };
    }

    public boolean isAsyncVirtual() {
        return this == ASYNC_VIRTUAL;
    }

    public boolean isLiveSynchronous() {
        return this == PRESENCIAL || this == REMOTE_LIVE;
    }
}
