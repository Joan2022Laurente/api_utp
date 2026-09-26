package com.utp.horario.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class AcademicActivity {
    private String id;
    private String title;
    private String activityType; // HOMEWORK, FORUM, EVALUATION
    private Integer weekNumber;
    private String startAt;
    private String finishAt;
    private String courseName;
    private String courseId;
    private String sectionId;
    private String contentId;
    private String activityId;
    private String evaluationSystem;
    private String studentStatus; // DELIVERED, MISSING, PENDING, PROGRAMMED
    private Boolean isQualified;
    private String classificationCategory; // WEIGHTED_EVALUATION, PRACTICE_HOMEWORK, PARTICIPATION_FORUM, EXAM, GENERAL_ACTIVITY
    private String urgency; // OVERDUE, DUE_TODAY, DUE_THIS_WEEK, UPCOMING, UNKNOWN
    private Long daysRemaining;
}
