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
public class Syllabus {
    private String id;
    private String courseCode;
    private String courseName;
    private String semester;
    private Integer credits;
    private String modality;
    private Integer weeklyHours;
    private List<String> careers;
    private String learningGoal;
    private String formula;
    private List<SyllabusEvaluation> evaluations;
    private List<String> rules;
    private Integer maxSimilarityPercent;
    private String aiPolicy;
    private List<SyllabusWeeklySession> weeklySchedule;
}
