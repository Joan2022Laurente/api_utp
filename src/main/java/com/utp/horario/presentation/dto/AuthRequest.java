package com.utp.horario.presentation.dto;

import com.utp.horario.domain.model.StudentProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {
    private String username;
    private String password;
    private String token;
}
