package com.utp.horario.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSyncEntity {
    @Id
    private String id;
    private String studentId;
    private String courseName;
    private String sectionId;
    private String homeworkId;
    private String title;
    private String type;
    private Integer week;
    private String homeworkStatus;
    private String assignmentProgress;
    private LocalDateTime dueDate;
    private LocalDateTime deliveredDate;
    private Double maxScore;
    private Double score;
    private Boolean isDelivered;
}
