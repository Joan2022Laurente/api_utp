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
public class ClassSession {
    private String id;
    private String courseCode;
    private String courseName;
    private String section;
    private String classroom;
    private String building;
    private String teacher;
    private String modality; // P (Presencial), R (Remoto), V (Virtual)
    private LocalDateTime startAt;
    private LocalDateTime finishAt;
    private String zoomLink;
    private String floor;
    private String environmentType;
    private String classLink;
}
