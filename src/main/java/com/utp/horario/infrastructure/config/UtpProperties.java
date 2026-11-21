package com.utp.horario.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propiedades de configuración para las pasarelas y servicios institucionales UTP.
 * Permite redefinir dominios y endpoints upstream en caliente mediante variables de entorno.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "utp")
public class UtpProperties {

    /**
     * Endpoint GraphQL del Portal UTP (ej. https://api-portal.utpxpedition.com/graphql).
     */
    private String portalUrl = "https://api-portal.utpxpedition.com/graphql";

    /**
     * URL base de la pasarela PAO UTP+ Class (ej. https://api-pao.utpxpedition.com).
     */
    private String paoBaseUrl = "https://api-pao.utpxpedition.com";

    /**
     * Namespace UUID v5 para derivación de paoUserId.
     */
    private String paoUuidNamespace = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";

    /**
     * Mapeo estático de fallback paoUserId por código de alumno.
     */
    private String paoUserMap = "";

    /**
     * ID de periodo académico predeterminado (ej. 2263 para 2026-2).
     */
    private String defaultPeriodId = "2263";

    /**
     * Nombre textual del periodo académico predeterminado.
     */
    private String defaultPeriodName = "2026 - Ciclo 2 Agosto";

    /**
     * Fecha de inicio oficial del periodo académico en formato ISO (yyyy-MM-dd).
     */
    private String defaultPeriodStartDate = "2026-08-10";
}
