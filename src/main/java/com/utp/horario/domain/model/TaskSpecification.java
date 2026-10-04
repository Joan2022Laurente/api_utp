package com.utp.horario.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TaskSpecification {
    private String id;
    private String title;
    private String courseCode; // Código oficial rector UTP (ej. 100000S72V)
    private String sectionId;  // UUID de la sección en Class UTP
    private String descriptionMarkdown;
    private String deliverablesMarkdown;
    private Integer maxAttempts;
    private List<String> submissionTypes;
    private String availableFrom;
    private String availableUntil;
    private String dueAt;
    private String unlockAt;
    private String lockAt;
    private String evaluationSystem;
    private Boolean isGroup;
    private String homeworkStatus;
    private Double evaluationTopScore;
    private String rubricName;
    private Double rubricScore;
    private List<RubricCriterion> gradingRubric;
    private SyllabusCorrelation syllabusCorrelation; // Vinculación con el sílabo oficial
}
