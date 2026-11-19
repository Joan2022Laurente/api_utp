package com.utp.horario.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassSession {
    private String id;
    /** Código de catálogo (ej. 100000SI97). NUNCA el título del curso; nullable si la UTP no lo entrega. */
    private String courseCode;
    private String courseName;
    /** Número de sección institucional (classNumber, ej. 20731). */
    private String section;
    /** UUID de la sección en Canvas PAO, extraído de `class.linkCourseClass`. */
    private String sectionId;
    /** Modalidad institucional resuelta desde el dashboard, tipada. */
    private ModalityType modalityType;
    private String classroom;
    private String building;
    private String teacher;
    private String modality; // P (Presencial), R (Remoto), V (Virtual)
    private LocalDateTime startAt;
    private LocalDateTime finishAt;
    private String zoomLink;
    private String floor;
    private String environmentType;
    private String classLink;

    public boolean isAsyncVirtual() {
        return modalityType != null && modalityType.isAsyncVirtual();
    }

    public boolean isLiveSynchronous() {
        return modalityType != null && modalityType.isLiveSynchronous();
    }
}
