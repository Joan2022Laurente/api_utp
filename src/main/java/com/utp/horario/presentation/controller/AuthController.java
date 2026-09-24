package com.utp.horario.presentation.controller;

import com.utp.horario.domain.model.StudentProfile;
import com.utp.horario.domain.port.in.AuthenticateStudentUseCase;
import com.utp.horario.presentation.dto.ApiResponse;
import com.utp.horario.presentation.dto.AuthRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticateStudentUseCase authenticateStudentUseCase;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<StudentProfile>> login(@RequestBody AuthRequest request) {
        StudentProfile profile;
        if (request.getToken() != null && !request.getToken().isBlank()) {
            profile = authenticateStudentUseCase.authenticateWithToken(request.getToken());
        } else {
            profile = authenticateStudentUseCase.authenticateWithCredentials(request.getUsername(), request.getPassword());
        }
        return ResponseEntity.ok(ApiResponse.ok("Autenticación exitosa", profile));
    }

    @GetMapping("/profile/{id}")
    public ResponseEntity<ApiResponse<StudentProfile>> getProfile(@PathVariable String id) {
        StudentProfile profile = authenticateStudentUseCase.getProfile(id);
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }
}
