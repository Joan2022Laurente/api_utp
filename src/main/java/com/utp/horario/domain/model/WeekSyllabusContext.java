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
public class WeekSyllabusContext {
    private Integer weekNumber;
    private String unitTitle;
    private String topic;
    private String activities;
    private String learningGoal;
    private List<SyllabusEvaluation> scheduledEvaluations;
}
