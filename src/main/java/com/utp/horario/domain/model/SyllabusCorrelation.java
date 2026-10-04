package com.utp.horario.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyllabusCorrelation {
    private String courseCode;
    private String evaluationType;         // ej: "AP2", "PC1", "EF", "PROY"
    private Integer weightPercent;          // ej: 20
    private String evaluationDescription;   // ej: "AVANCE DE PORTAFOLIO 2"
    private Integer syllabusWeek;           // ej: 7
    private String syllabusUnit;            // ej: "Unidad 2"
    private String syllabusTopic;           // ej: "Herramientas de comunicación efectiva"
    private Boolean isSyllabusMatched;      // true si hubo match contra evaluación oficial del sílabo
    private String syllabusUrl;             // ej: "/api/v1/syllabus/100000S72V"
    private String syllabusMarkdownUrl;     // ej: "/api/v1/syllabus/100000S72V/markdown"
}
