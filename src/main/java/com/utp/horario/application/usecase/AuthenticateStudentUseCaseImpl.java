package com.utp.horario.application.usecase;

import com.utp.horario.domain.model.StudentProfile;
import com.utp.horario.domain.port.in.AuthenticateStudentUseCase;
import com.utp.horario.domain.port.out.StudentRepositoryPort;
import com.utp.horario.domain.port.out.UtpPortalGatewayPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthenticateStudentUseCaseImpl implements AuthenticateStudentUseCase {

    private final StudentRepositoryPort studentRepositoryPort;
    private final UtpPortalGatewayPort utpPortalGatewayPort;

    @Override
    public StudentProfile authenticateWithCredentials(String username, String password) {
        StudentProfile profile = utpPortalGatewayPort.login(username, password);
        return studentRepositoryPort.save(profile);
    }

    @Override
    public StudentProfile authenticateWithToken(String token) {
        if (token != null && token.contains(".")) {
            try {
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
                    com.fasterxml.jackson.databind.JsonNode payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(decoded);
                    String studentCode = payload.hasNonNull("preferred_username") 
                            ? payload.path("preferred_username").asText().toUpperCase() 
                            : "";
                    String name = payload.hasNonNull("name") 
                            ? payload.path("name").asText() 
                            : (payload.hasNonNull("given_name") ? payload.path("given_name").asText() : studentCode);
                    String email = payload.hasNonNull("email") 
                            ? payload.path("email").asText() 
                            : (!studentCode.isBlank() ? studentCode.toLowerCase() + "@utp.edu.pe" : "");
                    String userId = payload.hasNonNull("userId") 
                            ? payload.path("userId").asText() 
                            : (payload.hasNonNull("sub") ? payload.path("sub").asText() : (!studentCode.isBlank() ? "usr-" + studentCode.toLowerCase() : "usr-guest"));

                    String career = payload.hasNonNull("career") ? payload.path("career").asText() : (payload.hasNonNull("carrera") ? payload.path("carrera").asText() : "");
                    String campus = payload.hasNonNull("campus") ? payload.path("campus").asText() : (payload.hasNonNull("campusDesc") ? payload.path("campusDesc").asText() : (payload.hasNonNull("sede") ? payload.path("sede").asText() : ""));
                    int cycle = payload.hasNonNull("cycle") ? payload.path("cycle").asInt(1) : (payload.hasNonNull("ciclo") ? payload.path("ciclo").asInt(1) : 1);

                    StudentProfile profile = StudentProfile.builder()
                            .id(userId)
                            .studentCode(studentCode)
                            .fullName(name)
                            .email(email)
                            .career(career)
                            .campus(campus)
                            .currentCycle(cycle)
                            .token(token)
                            .enrolledCourseCodes(List.of())
                            .build();
                    return studentRepositoryPort.save(profile);
                }
            } catch (Exception e) {
                // fall through
            }
        }
        throw new IllegalArgumentException("Token de autenticación UTP inválido o expirado");
    }

    @Override
    public StudentProfile getProfile(String studentId) {
        return studentRepositoryPort.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Estudiante no encontrado: " + studentId));
    }
}
