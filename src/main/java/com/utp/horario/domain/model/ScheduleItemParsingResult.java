package com.utp.horario.domain.model;

/**
 * Resultado del parseo de un ítem de {@code scheduleByDate}, sin descartes ciegos.
 *
 * <p>Tres variantes explícitas: sesión válida para la grilla, referencia asíncrona que se
 * preserva en {@code courses[]} y rechazo honesto por datos corruptos de UTP (que marca el
 * enriquecimiento como {@code PARTIAL}).</p>
 */
public sealed interface ScheduleItemParsingResult
        permits ScheduleItemParsingResult.Session,
                ScheduleItemParsingResult.AsyncCourseRef,
                ScheduleItemParsingResult.Rejected {

    /** Clase presencial o virtual sincrónica válida (ingresa a la grilla semanal). */
    record Session(ClassSession session) implements ScheduleItemParsingResult {
    }

    /** Asignatura 24/7: se excluye de {@code classes[]} pero se preserva en {@code courses[]}. */
    record AsyncCourseRef(String courseCode, String sectionId, String name, String classLink)
            implements ScheduleItemParsingResult {
    }

    /** Ítem corrupto de UTP: se contabiliza y deja {@code enrichmentStatus = PARTIAL}. */
    record Rejected(String reason) implements ScheduleItemParsingResult {
    }
}
