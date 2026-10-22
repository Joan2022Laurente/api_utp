package com.utp.horario.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Limitador de peticiones en memoria, por direccion IP y ventana temporal deslizante.
 *
 * <p>Protege el endpoint de inicio de sesion frente a la fuerza bruta de credenciales
 * contra el SSO institucional. No requiere dependencias externas y el estado vive solo en
 * la memoria del proceso: al reiniciar el dyno el contador vuelve a cero, lo cual es
 * aceptable para un limite de tasa.</p>
 *
 * <p>Se contabilizan dos ventanas por IP: los <b>intentos totales</b> (para frenar el
 * volumen) y los <b>intentos fallidos</b> (para frenar la fuerza bruta, que genera
 * muchos 401).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private final RateLimitProperties properties;

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    /**
     * Decision del limitador ante un intento de autenticacion.
     */
    public enum Decision {
        /** El intento se admite. */
        ALLOWED,
        /** Se supera el limite de intentos totales. */
        BLOCKED_BY_RATE,
        /** Se supera el limite de intentos fallidos. */
        BLOCKED_BY_FAILURES
    }

    /**
     * Evalua si se admite un intento de autenticacion para la IP indicada.
     */
    public Decision tryAcquire(String clientKey) {
        if (!properties.isEnabled()) {
            return Decision.ALLOWED;
        }
        long now = System.currentTimeMillis();
        Deque<Long> attemptWindow = attempts.computeIfAbsent(clientKey, k -> new ConcurrentLinkedDeque<>());
        Deque<Long> failureWindow = failures.computeIfAbsent(clientKey, k -> new ConcurrentLinkedDeque<>());
        prune(attemptWindow, now);
        prune(failureWindow, now);

        if (failureWindow.size() >= properties.getMaxFailuresPerWindow()) {
            return Decision.BLOCKED_BY_FAILURES;
        }
        if (attemptWindow.size() >= properties.getMaxRequestsPerWindow()) {
            return Decision.BLOCKED_BY_RATE;
        }
        attemptWindow.addLast(now);
        return Decision.ALLOWED;
    }

    /**
     * Registra un intento fallido (respuesta 401 del controlador).
     */
    public void recordFailure(String clientKey) {
        if (!properties.isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        Deque<Long> failureWindow = failures.computeIfAbsent(clientKey, k -> new ConcurrentLinkedDeque<>());
        prune(failureWindow, now);
        failureWindow.addLast(now);
    }

    /**
     * Segundos que deben esperar el cliente antes de reintentar.
     */
    public long retryAfterSeconds() {
        return Math.max(1, properties.getWindowSeconds());
    }

    /**
     * Elimina de la ventana las marcas temporales que ya han expirado.
     */
    private void prune(Deque<Long> window, long now) {
        long cutoff = now - properties.getWindowSeconds() * 1000L;
        while (!window.isEmpty() && window.peekFirst() <= cutoff) {
            window.pollFirst();
        }
    }
}
