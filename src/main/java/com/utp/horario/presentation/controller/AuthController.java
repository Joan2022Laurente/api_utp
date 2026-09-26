package com.utp.horario.presentation.controller;

import com.utp.horario.domain.model.StudentProfile;
import com.utp.horario.domain.port.in.AuthenticateStudentUseCase;
import com.utp.horario.presentation.dto.ApiResponse;
import com.utp.horario.presentation.dto.LoginRequest;
import com.utp.horario.presentation.dto.RefreshTokenRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticación", description = "Autenticación institucional de estudiantes mediante SSO Keycloak UTP")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticateStudentUseCase authenticateStudentUseCase;

    @Operation(summary = "Iniciar sesión institucional", description = "Autentica al estudiante con su código y contraseña UTP contra el SSO Keycloak. Retorna el perfil y los tokens de sesión (access_token y refresh_token).")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<StudentProfile>> login(@Valid @RequestBody LoginRequest request) {
        StudentProfile profile = authenticateStudentUseCase.authenticateWithCredentials(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.ok("Autenticación exitosa", profile));
    }

    @Operation(summary = "Renovar sesión (Refresh Token)", description = "Obtiene un nuevo access_token y refresh_token usando el refresh_token emitido por Keycloak sin requerir reingresar credenciales.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<StudentProfile>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        StudentProfile profile = authenticateStudentUseCase.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok("Sesión renovada exitosamente", profile));
    }

    @Operation(summary = "Consultar identidad actual (Stateless / me)", description = "Valida el token Bearer en memoria e inspecciona los datos del estudiante de forma inmediata sin consultar a Keycloak.",
               security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<StudentProfile>> getMe(
            @Parameter(hidden = true)
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Cabecera 'Authorization: Bearer <token>' requerida.");
        }
        String token = authHeader.substring(7).trim();
        StudentProfile profile = authenticateStudentUseCase.authenticateWithToken(token);
        return ResponseEntity.ok(ApiResponse.ok("Sesión válida", profile));
    }

    @Operation(summary = "Consultar perfil en repositorio por ID", description = "Obtiene perfil previamente guardado en el repositorio interno.",
               security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/profile/{id}")
    public ResponseEntity<ApiResponse<StudentProfile>> getProfile(
            @PathVariable String id,
            @Parameter(hidden = true)
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Cabecera 'Authorization: Bearer <token>' requerida.");
        }
        String token = authHeader.substring(7).trim();
        StudentProfile caller = authenticateStudentUseCase.authenticateWithToken(token);
        if (!caller.getId().equalsIgnoreCase(id) && !caller.getStudentCode().equalsIgnoreCase(id)) {
            throw new SecurityException("Acceso no autorizado: no tienes permisos para consultar el perfil de otro estudiante.");
        }
        StudentProfile profile = authenticateStudentUseCase.getProfile(caller.getId());
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }
}
