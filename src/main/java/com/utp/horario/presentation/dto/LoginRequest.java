package com.utp.horario.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Credenciales institucionales UTP para emisión de sesión")
public class LoginRequest {

    @NotBlank(message = "El código de estudiante (username) es obligatorio")
    @Pattern(regexp = "^[uU]?\\d{8,9}$", message = "El código debe ser un formato de estudiante UTP válido (ej. U23307609 o 23307609)")
    @Schema(description = "Código de estudiante institucional UTP", example = "u23307609", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "La contraseña es obligatoria")
    @Schema(description = "Contraseña de la cuenta institucional UTP", example = "MiPassword123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
