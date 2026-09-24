package com.utp.horario.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSyncItem {
    private String id;
    private String courseName;
    private String sectionId;
    private String homeworkId;
    private String title;
    private String type;
    private Integer week;
    private String homeworkStatus; // DELIVERED, PENDING, GRADED
    private String assignmentProgress; // FINISHED, IN_PROGRESS, NOT_STARTED
    private LocalDateTime dueDate;
    private LocalDateTime deliveredDate;
    private Double maxScore;
    private Double score;
    private Boolean isDelivered;
}
