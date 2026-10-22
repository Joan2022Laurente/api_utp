package com.utp.horario.infrastructure.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Limite de peticiones del endpoint de inicio de sesion.
 *
 * <p>Sin este limite, el login de la pasarela queda expuesto a fuerza bruta contra el SSO
 * institucional de la UTP: cada intento correcto o fallido consume un recurso del realm
 * Keycloak. El limite se aplica por direccion IP de origen y por ventana temporal.</p>
 *
 * <p>Los valores por defecto son deliberadamente generosos (10 intentos por minuto) para no
 * afectar al uso legitimo, incluida la reautenticacion automatica de las apps cliente.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "utp.security.rate-limit")
public class RateLimitProperties {

    /** Si el limite esta activo. */
    private boolean enabled = true;

    /** Intentos totales admitidos por IP dentro de la ventana. */
    private int maxRequestsPerWindow = 10;

    /** Intentos fallidos admitidos por IP dentro de la ventana. */
    private int maxFailuresPerWindow = 5;

    /** Duracion de la ventana, en segundos. */
    private long windowSeconds = 60L;
}
