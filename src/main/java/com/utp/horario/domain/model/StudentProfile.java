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
public class StudentProfile {
    private String id;
    private String studentCode;
    private String fullName;
    private String email;
    private String career;
    private String campus;
    private Integer currentCycle;
    private String token;
    private List<String> enrolledCourseCodes;
}
