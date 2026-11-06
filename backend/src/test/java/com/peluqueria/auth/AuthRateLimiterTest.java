package com.peluqueria.auth;

import com.peluqueria.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthRateLimiterTest {

    @Test
    void permiteHastaElMaximoDeIntentos() {
        AuthRateLimiter limiter = new AuthRateLimiter();
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiter.check("login", "1.2.3.4", 5, 300));
        }
    }

    @Test
    void bloqueaAlSuperarElMaximo() {
        AuthRateLimiter limiter = new AuthRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.check("login", "1.2.3.4", 5, 300);
        }

        ApiException ex = assertThrows(ApiException.class, () -> limiter.check("login", "1.2.3.4", 5, 300));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
    }

    @Test
    void noMezclaContadoresDeIpsDistintas() {
        AuthRateLimiter limiter = new AuthRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.check("login", "1.2.3.4", 5, 300);
        }

        // Otra IP para la misma accion no deberia verse afectada por el limite de la anterior.
        assertDoesNotThrow(() -> limiter.check("login", "5.6.7.8", 5, 300));
    }

    @Test
    void noMezclaContadoresDeAccionesDistintasParaLaMismaIp() {
        AuthRateLimiter limiter = new AuthRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.check("login", "1.2.3.4", 5, 300);
        }

        // Misma IP pero otra accion: su propio contador, independiente del de "login".
        assertDoesNotThrow(() -> limiter.check("forgot-password", "1.2.3.4", 5, 300));
    }

    @Test
    void elContadorSeReiniciaCuandoExpiraLaVentana() throws InterruptedException {
        AuthRateLimiter limiter = new AuthRateLimiter();
        long windowSeconds = 1;
        for (int i = 0; i < 3; i++) {
            limiter.check("login", "9.9.9.9", 3, windowSeconds);
        }
        assertThrows(ApiException.class, () -> limiter.check("login", "9.9.9.9", 3, windowSeconds));

        Thread.sleep(1200);

        assertDoesNotThrow(() -> limiter.check("login", "9.9.9.9", 3, windowSeconds));
    }
}
