package com.utp.horario.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud para renovación de sesión mediante refresh token")
public class RefreshTokenRequest {

    @NotBlank(message = "El refresh token es obligatorio")
    @Schema(description = "Refresh token de Keycloak UTP obtenido en el inicio de sesión", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVC...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}
