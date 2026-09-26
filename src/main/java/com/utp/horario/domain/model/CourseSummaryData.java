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
public class CourseSummaryData {
    private String periodId;
    private PeriodSummary summary;
    private List<CourseGradeItem> courses;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodSummary {
        private String campus;
        private String enrolledCourses;
        private String average;
        private String relativeCycle;
        private String creditCount;
        private String meritOrder;
        private String weeklyHours;
        private String meritBelong;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseGradeItem {
        private String courseId;
        private String courseCode;
        private String courseName;
        private String section;
        private String credits;
        private String formula;
        private String teacher;
        private String average;
        private List<EvaluationGrade> evaluations;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluationGrade {
        private String name;
        private String shortName; // PC1, PC2, AP1, etc.
        private String value;     // Nota obtenida o "0.02" para pendiente
        private Boolean isGraded;
    }
}
