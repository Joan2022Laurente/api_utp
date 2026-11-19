package com.utp.horario.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course {
    private String id;
    /** Código de catálogo (100000...). Nunca el nombre; nullable si la fuente no lo provee. */
    private String code;
    private String name;
    /** Número de sección institucional (classNumber). */
    private String section;
    /** UUID de la sección en Canvas PAO. */
    private String sectionId;
    /** Enlace al aula virtual Canvas, preservado aunque el curso no tenga sesiones. */
    private String classLink;
    private ModalityType modalityType;
    private Integer credits;
    private String teacher;
    private String modality;
    private Integer weeklyHours;
    private List<ClassSession> sessions;

    public boolean isAsyncVirtual() {
        return modalityType != null && modalityType.isAsyncVirtual();
    }

    public boolean isLiveSynchronous() {
        return modalityType != null && modalityType.isLiveSynchronous();
    }
}
