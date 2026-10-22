package com.utp.horario.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.domain.model.StudentProfile;
import com.utp.horario.domain.port.in.AuthenticateStudentUseCase;
import com.utp.horario.presentation.dto.LoginRequest;
import com.utp.horario.presentation.dto.RefreshTokenRequest;
import com.utp.horario.presentation.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthenticateStudentUseCase authenticateStudentUseCase;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        authenticateStudentUseCase = Mockito.mock(AuthenticateStudentUseCase.class);
        AuthController authController = new AuthController(authenticateStudentUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void login_withValidCredentials_returnsProfileAndSessionTokens() throws Exception {
        StudentProfile mockProfile = StudentProfile.builder()
                .id("usr-123")
                .studentCode("U99999999")
                .fullName("JUAN PEREZ")
                .email("U99999999@utp.edu.pe")
                .career("INGENIERIA DE SISTEMAS")
                .campus("LIMA CENTRO")
                .currentCycle(5)
                .token("jwt.access.token")
                .refreshToken("jwt.refresh.token")
                .expiresIn(1800)
                .enrolledCourseCodes(List.of())
                .build();

        when(authenticateStudentUseCase.authenticateWithCredentials("U99999999", "secretPass"))
                .thenReturn(mockProfile);

        LoginRequest request = LoginRequest.builder()
                .username("U99999999")
                .password("secretPass")
                .build();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.studentCode").value("U99999999"))
                .andExpect(jsonPath("$.data.token").value("jwt.access.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("jwt.refresh.token"))
                .andExpect(jsonPath("$.data.expiresIn").value(1800));
    }

    @Test
    void login_withInvalidFormatOrBlank_returns400() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("invalido")
                .password("")
                .build();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("El login acepta un token de sesion vigente en lugar de credenciales")
    void login_withToken_returnsProfileWithoutCredentials() throws Exception {
        StudentProfile mockProfile = StudentProfile.builder()
                .id("sub-u99999999")
                .studentCode("U99999999")
                .fullName("JUAN PEREZ")
                .email("u99999999@utp.edu.pe")
                .token("jwt.access.token")
                .build();

        when(authenticateStudentUseCase.authenticateWithToken("jwt.access.token"))
                .thenReturn(mockProfile);

        LoginRequest request = LoginRequest.builder()
                .token("jwt.access.token")
                .build();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.studentCode").value("U99999999"))
                .andExpect(jsonPath("$.data.token").value("jwt.access.token"));
    }

    @Test
    @DisplayName("El login sin credenciales ni token responde 400")
    void login_withoutCredentialsOrToken_returns400() throws Exception {
        LoginRequest request = LoginRequest.builder().build();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void me_withValidBearerHeader_returnsStudentProfile() throws Exception {
        StudentProfile mockProfile = StudentProfile.builder()
                .id("usr-123")
                .studentCode("U99999999")
                .fullName("JUAN PEREZ")
                .token("mocked.jwt.token")
                .build();

        when(authenticateStudentUseCase.authenticateWithToken("mocked.jwt.token"))
                .thenReturn(mockProfile);

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer mocked.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.studentCode").value("U99999999"));
    }

    @Test
    void me_withoutBearerHeader_returns400() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Cabecera 'Authorization: Bearer <token>' requerida."));
    }

    @Test
    void refresh_withValidRefreshToken_returnsUpdatedProfile() throws Exception {
        StudentProfile mockProfile = StudentProfile.builder()
                .id("usr-123")
                .studentCode("U99999999")
                .token("new.jwt.token")
                .refreshToken("new.refresh.token")
                .expiresIn(1800)
                .build();

        when(authenticateStudentUseCase.refreshToken("valid.refresh.token"))
                .thenReturn(mockProfile);

        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("valid.refresh.token")
                .build();

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("new.jwt.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new.refresh.token"));
    }

    @Test
    void getProfileById_whenSameUser_returns200Ok() throws Exception {
        StudentProfile mockProfile = StudentProfile.builder()
                .id("usr-123")
                .studentCode("U99999999")
                .fullName("JUAN PEREZ")
                .token("mocked.jwt.token")
                .build();

        when(authenticateStudentUseCase.authenticateWithToken("mocked.jwt.token"))
                .thenReturn(mockProfile);
        when(authenticateStudentUseCase.getProfile("usr-123"))
                .thenReturn(mockProfile);

        mockMvc.perform(get("/auth/profile/usr-123")
                        .header("Authorization", "Bearer mocked.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.studentCode").value("U99999999"));
    }

    @Test
    void getProfileById_whenDifferentUser_returns403Forbidden() throws Exception {
        StudentProfile mockCaller = StudentProfile.builder()
                .id("usr-caller-123")
                .studentCode("U99999999")
                .fullName("ALUMNO ATACANTE")
                .token("mocked.jwt.token")
                .build();

        when(authenticateStudentUseCase.authenticateWithToken("mocked.jwt.token"))
                .thenReturn(mockCaller);

        // Intenta consultar a la víctima "usr-victim-999"
        mockMvc.perform(get("/auth/profile/usr-victim-999")
                        .header("Authorization", "Bearer mocked.jwt.token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Acceso no autorizado: no tienes permisos para consultar el perfil de otro estudiante."));
    }
}
