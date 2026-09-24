package com.utp.horario.domain.port.in;

import com.utp.horario.domain.model.StudentProfile;

public interface AuthenticateStudentUseCase {
    StudentProfile authenticateWithCredentials(String username, String password);
    StudentProfile authenticateWithToken(String token);
    StudentProfile getProfile(String studentId);
}
