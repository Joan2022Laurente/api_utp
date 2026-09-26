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
@Schema(description = "Solicitud de autenticación. Envíe username/password O un token JWT existente.")
public class AuthRequest {

    @Schema(description = "Código de estudiante UTP (ej. u23307609 o 23307609)", example = "u23307609", nullable = true)
    private String username;

    @Schema(description = "Contraseña de la cuenta UTP", example = "TuPassword123", nullable = true)
    private String password;

    @Schema(description = "Token JWT previo (opcional; si se envía, no se requieren credenciales)", example = "", nullable = true)
    private String token;
}
