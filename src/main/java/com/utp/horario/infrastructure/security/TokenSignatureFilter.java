package com.utp.horario.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utp.horario.presentation.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro que verifica la firma del token de sesion antes de que la peticion alcance los
 * controladores.
 *
 * <p>Es el punto de imposicion de la verificacion criptografica. Su comportamiento esta
 * gobernado por {@link JwtVerificationProperties#getMode()}:</p>
 * <ul>
 *   <li>{@code OFF}: el filtro no hace nada (comportamiento heredado).</li>
 *   <li>{@code AUDIT}: verifica y registra el resultado, pero deja pasar la peticion. Sirve
 *       para observar en produccion que todo token legitimo supera la validacion.</li>
 *   <li>{@code ENFORCE}: un token invalido se responde con {@code 401 Unauthorized} dentro
 *       del sobre {@code ApiResponse}, igual que el resto de errores de la API.</li>
 * </ul>
 *
 * <p><b>Garantia de no ruptura del contrato:</b> una peticion <b>sin token</b> no se ve
 * afectada en ningun modo (se comporta exactamente igual que antes). Solo cambia la
 * respuesta de las peticiones que aportan un token que no supera la verificacion, que hasta
 * ahora se aceptaban sin comprobar nada.</p>
 *
 * <p>Se aplica como filtro de servlet (fuera de la cadena de Spring Security) para no
 * alterar la configuracion de seguridad existente, que hoy deja pasar todas las rutas.</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TokenSignatureFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtVerificationProperties properties;
    private final JwtSignatureVerifier verifier;
    private final ObjectMapper objectMapper;

    public TokenSignatureFilter(JwtVerificationProperties properties,
                                JwtSignatureVerifier verifier,
                                ObjectMapper objectMapper) {
        this.properties = properties;
        this.verifier = verifier;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (properties.getMode() == JwtVerificationProperties.Mode.OFF) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isSkipped(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        String queryToken = extractQueryToken(request);

        if ((authorization == null || authorization.isBlank()) && (queryToken == null || queryToken.isBlank())) {
            // Sin token se conserva el comportamiento previo: esta correccion trata de los
            // tokens FALSOS, no de las peticiones sin autenticar.
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization != null && !authorization.isBlank()
                ? stripBearer(authorization)
                : queryToken;

        JwtSignatureVerifier.Outcome outcome = verifier.verify(token);
        TokenVerificationPolicy.Decision decision = verifier.decide(outcome);

        if (!outcome.verifiable()) {
            // El JWKS no pudo descargarse: no es un ataque, es una caida del SSO.
            log.error("[JWT-AUDIT] Firma no verificable (JWKS no disponible) en {} {}: {}",
                    request.getMethod(), request.getRequestURI(), outcome.reason());
        } else if (!outcome.valid()) {
            log.warn("[JWT] Token invalido en {} {} (kid={}, alg={}): {}",
                    request.getMethod(), request.getRequestURI(), outcome.kid(), outcome.algorithm(),
                    outcome.reason());
        }

        switch (decision) {
            case REJECT -> writeUnauthorized(response, outcome);
            case LOG_ONLY -> {
                log.warn("[JWT-AUDIT] Peticion admitida pese a no superar la verificacion (modo {}): {} {}",
                        properties.getMode(), request.getMethod(), request.getRequestURI());
                filterChain.doFilter(request, response);
            }
            default -> filterChain.doFilter(request, response);
        }
    }

    /**
     * Extrae el token que algunos clientes siguen enviando como parametro de consulta.
     *
     * <p>El parametro {@code token} en la URL es una practica heredada que expone el token
     * en los registros del servidor y en la cabecera {@code Referer}. Se sigue aceptando
     * para no romper a los clientes existentes, pero el token se verifica igual que si
     * llegase en la cabecera {@code Authorization}.</p>
     */
    private String extractQueryToken(HttpServletRequest request) {
        if (!relativePath(request).endsWith("/schedule/sync")) {
            return null;
        }
        String token = request.getParameter("token");
        if (token == null || token.isBlank()) {
            return null;
        }
        return token.trim();
    }

    /**
     * Normaliza el encabezado Authorization y devuelve el token puro.
     */
    private String stripBearer(String authorization) {
        String value = authorization.trim();
        if (value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return value.substring(BEARER_PREFIX.length()).trim();
        }
        return value;
    }

    /**
     * Indica si la ruta esta exenta de verificacion.
     */
    private boolean isSkipped(HttpServletRequest request) {
        String path = relativePath(request);
        for (String skip : properties.getSkipPaths()) {
            if (skip == null || skip.isBlank()) {
                continue;
            }
            if (path.equals(skip) || path.startsWith(skip.endsWith("/") ? skip : skip + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ruta de la peticion relativa al context-path de la aplicacion.
     */
    private static String relativePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
    }

    /**
     * Escribe el error 401 respetando el sobre {@code ApiResponse} del contrato.
     */
    private void writeUnauthorized(HttpServletResponse response,
                                   JwtSignatureVerifier.Outcome outcome) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        response.setHeader("Cache-Control", "no-store");
        ApiResponse<Void> body = ApiResponse.fail(
                "Token de sesion invalido o expirado. Renueve la sesion con POST /auth/login.");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
