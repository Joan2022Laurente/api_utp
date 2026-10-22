package com.utp.horario.presentation.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validador de {@link ValidLoginPayload}.
 *
 * <p>Reproduce exactamente las reglas y los mensajes que se aplicaban antes sobre los
 * campos sueltos del cuerpo de la solicitud, pero admitiendo ademas la autenticacion por
 * token.</p>
 */
public class LoginPayloadValidator implements ConstraintValidator<ValidLoginPayload, LoginRequest> {

    /** Mensaje historico para un codigo de alumno ausente. */
    static final String USERNAME_REQUIRED = "El código de estudiante (username) es obligatorio";

    /** Mensaje historico para un codigo de alumno con formato invalido. */
    static final String USERNAME_FORMAT =
            "El código debe ser un formato de estudiante UTP válido (ej. U23307609 o 23307609)";

    /** Mensaje historico para una contrasena ausente. */
    static final String PASSWORD_REQUIRED = "La contraseña es obligatoria";

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[uU]?\\d{8,9}$");

    @Override
    public boolean isValid(LoginRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        // Si llega un token, la sesion se renova con el y no hacen falta credenciales.
        if (request.getToken() != null && !request.getToken().isBlank()) {
            return true;
        }

        List<String> errors = new ArrayList<>();

        String username = request.getUsername();
        if (username == null || username.isBlank()) {
            errors.add(USERNAME_REQUIRED);
        } else if (!USERNAME_PATTERN.matcher(username.trim()).matches()) {
            errors.add(USERNAME_FORMAT);
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            errors.add(PASSWORD_REQUIRED);
        }

        if (errors.isEmpty()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        for (String error : errors) {
            context.buildConstraintViolationWithTemplate(error).addConstraintViolation();
        }
        return false;
    }
}
