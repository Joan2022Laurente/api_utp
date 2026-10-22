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
 * Filtro que limita los intentos de autenticacion del endpoint {@code POST /auth/login}.
 *
 * <p>Su unico proposito es evitar la fuerza bruta de credenciales contra el SSO
 * institucional. La respuesta mantiene el sobre {@code ApiResponse} del contrato y anade la
 * cabecera {@code Retry-After} para que el cliente sepa cuando reintentar.</p>
 *
 * <p>La direccion del cliente se obtiene de {@code X-Forwarded-For}, que es lo que fija el
 * router de la plataforma de despliegue: usar la direccion remota directa no serviria de
 * nada, porque seria la misma para todas las peticiones que llegan al mismo dyno.</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/auth/login";

    private final LoginRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public LoginRateLimitFilter(LoginRateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !relativePath(request).endsWith(LOGIN_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientKey = resolveClientKey(request);
        LoginRateLimiter.Decision decision = rateLimiter.tryAcquire(clientKey);

        if (decision != LoginRateLimiter.Decision.ALLOWED) {
            log.warn("[RateLimit] Intento de autenticacion bloqueado para {} (motivo: {}).",
                    clientKey, decision);
            writeTooManyRequests(response, decision);
            return;
        }

        filterChain.doFilter(request, response);

        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            rateLimiter.recordFailure(clientKey);
        }
    }

    /**
     * Resuelve la clave de agrupacion del cliente.
     *
     * <p>Se toma el ultimo salto de {@code X-Forwarded-For}: en un despliegue tras un router
     * (Heroku, por ejemplo) ese ultimo valor es la direccion real del cliente que origino
     * la peticion. Si la cabecera no existe, se recurre a la direccion remota.</p>
     */
    static String resolveClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String candidate = hops[i].trim();
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }
        String remote = request.getRemoteAddr();
        return (remote == null || remote.isBlank()) ? "desconocida" : remote;
    }

    /**
     * Escribe la respuesta 429 respetando el sobre {@code ApiResponse} del contrato.
     */
    private void writeTooManyRequests(HttpServletResponse response,
                                      LoginRateLimiter.Decision decision) throws IOException {
        long retryAfter = rateLimiter.retryAfterSeconds();
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setHeader("Cache-Control", "no-store");
        String message = decision == LoginRateLimiter.Decision.BLOCKED_BY_FAILURES
                ? "Demasiados intentos fallidos de autenticacion. Espere " + retryAfter
                        + " segundos antes de reintentar."
                : "Demasiadas solicitudes de autenticacion. Espere " + retryAfter
                        + " segundos antes de reintentar.";
        ApiResponse<Void> body = ApiResponse.fail(message);
        objectMapper.writeValue(response.getWriter(), body);
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
}
