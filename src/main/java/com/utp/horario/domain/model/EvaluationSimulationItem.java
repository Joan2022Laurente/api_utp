package com.utp.horario.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationSimulationItem {
    private String shortName;
    private String name;
    private int weightPercentage;
    private Double currentGrade;
    private Boolean isGraded;
    private double pointsContributed;
}
