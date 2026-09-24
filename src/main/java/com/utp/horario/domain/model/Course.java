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
public class Course {
    private String id;
    private String code;
    private String name;
    private String section;
    private Integer credits;
    private String teacher;
    private String modality;
    private Integer weeklyHours;
    private List<ClassSession> sessions;
}
