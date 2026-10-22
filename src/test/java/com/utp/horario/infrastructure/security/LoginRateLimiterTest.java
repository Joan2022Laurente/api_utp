package com.utp.horario.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Pruebas del limitador de intentos de autenticacion.
 */
class LoginRateLimiterTest {

    private RateLimitProperties properties;
    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setMaxRequestsPerWindow(5);
        properties.setMaxFailuresPerWindow(3);
        properties.setWindowSeconds(60);
        limiter = new LoginRateLimiter(properties);
    }

    @Test
    @DisplayName("Los intentos por debajo del limite se admiten")
    void attemptsUnderTheLimitAreAllowed() {
        for (int i = 0; i < 5; i++) {
            assertEquals(LoginRateLimiter.Decision.ALLOWED, limiter.tryAcquire("1.2.3.4"));
        }
    }

    @Test
    @DisplayName("Superado el limite de intentos, la peticion se bloquea")
    void attemptsOverTheLimitAreBlocked() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("1.2.3.4");
        }

        assertEquals(LoginRateLimiter.Decision.BLOCKED_BY_RATE, limiter.tryAcquire("1.2.3.4"));
    }

    @Test
    @DisplayName("Los intentos fallidos bloquean antes que el limite de volumen")
    void failuresBlockEarlierThanTheRateLimit() {
        limiter.tryAcquire("1.2.3.4");
        limiter.recordFailure("1.2.3.4");
        limiter.tryAcquire("1.2.3.4");
        limiter.recordFailure("1.2.3.4");
        limiter.tryAcquire("1.2.3.4");
        limiter.recordFailure("1.2.3.4");

        assertEquals(LoginRateLimiter.Decision.BLOCKED_BY_FAILURES, limiter.tryAcquire("1.2.3.4"));
    }

    @Test
    @DisplayName("El limite es por direccion IP: una IP no afecta a otra")
    void theLimitIsPerClient() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("1.2.3.4");
        }
        assertEquals(LoginRateLimiter.Decision.BLOCKED_BY_RATE, limiter.tryAcquire("1.2.3.4"));

        assertEquals(LoginRateLimiter.Decision.ALLOWED, limiter.tryAcquire("5.6.7.8"),
                "Una IP distinta no debe verse afectada por el limite de otra");
    }

    @Test
    @DisplayName("Con el limitador desactivado no se bloquea nada")
    void disabledLimiterNeverBlocks() {
        properties.setEnabled(false);

        for (int i = 0; i < 50; i++) {
            assertEquals(LoginRateLimiter.Decision.ALLOWED, limiter.tryAcquire("1.2.3.4"));
        }
    }

    @Test
    @DisplayName("La ventana temporal expira y el contador se reinicia")
    void theWindowExpires() throws Exception {
        properties.setWindowSeconds(1);
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("1.2.3.4");
        }
        assertEquals(LoginRateLimiter.Decision.BLOCKED_BY_RATE, limiter.tryAcquire("1.2.3.4"));

        Thread.sleep(1100);

        assertEquals(LoginRateLimiter.Decision.ALLOWED, limiter.tryAcquire("1.2.3.4"),
                "Pasada la ventana, el contador debe reiniciarse");
    }

    @Test
    @DisplayName("El tiempo de espera sugerido coincide con la ventana configurada")
    void retryAfterMatchesTheWindow() {
        properties.setWindowSeconds(45);

        assertEquals(45, limiter.retryAfterSeconds());
        assertNotEquals(0, limiter.retryAfterSeconds());
    }
}
