package com.utp.horario.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Credenciales institucionales UTP para emisión de sesión. Se aceptan username y password, o alternativamente un token de sesión vigente.")
@ValidLoginPayload
public class LoginRequest {

    @Schema(description = "Código de estudiante institucional UTP. Obligatorio si no se envía token.",
            example = "u23307609")
    private String username;

    @Schema(description = "Contraseña de la cuenta institucional UTP. Obligatoria si no se envía token.",
            example = "MiPassword123")
    private String password;

    @Schema(description = "Token de sesión vigente (opcional). Si se envía, no se requieren credenciales y la sesión se renueva con ese token.",
            example = "", nullable = true)
    private String token;
}
