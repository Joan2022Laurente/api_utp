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
public class GradeSimulationResult {
    private String courseCode;
    private String courseName;
    private String formula;
    private double targetGrade;
    private double currentAccumulatedScore;
    private int gradedWeightPercentage;
    private int remainingWeightPercentage;
    private Double requiredAverageOnPending;
    private double minPossibleGrade;
    private double maxPossibleGrade;
    private boolean isPassed;
    private String status;
    private String message;
    private List<EvaluationSimulationItem> evaluations;
}
