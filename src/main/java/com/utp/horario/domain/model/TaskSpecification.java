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
public class TaskSpecification {
    private String id;
    private String title;
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
}
