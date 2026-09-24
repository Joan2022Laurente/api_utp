package com.utp.horario.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleInterval {
    private String id;
    private String periodName;
    private Integer weekNumber;
    private Integer totalWeeks;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<Course> courses;
    private List<ClassSession> classes;
}
