package com.utp.horario.presentation.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida que la solicitud de inicio de sesion contenga credenciales <b>o</b> un token.
 *
 * <p>El contrato publicado acepta las dos formas: {@code {username, password}} (autenticacion
 * contra el SSO) o {@code {token}} (renovacion de sesion a partir de un token vigente). La
 * validacion se resuelve a nivel de clase porque la exigencia depende del contenido: si
 * llega un token, no se requieren credenciales; si no, ambos campos son obligatorios.</p>
 *
 * <p>Los mensajes reproduced son los mismos que se emitian antes, de modo que el detalle
 * del error no cambia para los clientes existentes.</p>
 */
@Documented
@Constraint(validatedBy = LoginPayloadValidator.class)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidLoginPayload {

    String message() default "Se requieren credenciales (username y password) o un token de sesion vigente.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
