package com.utp.horario.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
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
    private String refreshToken;
    private Integer expiresIn;
    private List<String> enrolledCourseCodes;
}
