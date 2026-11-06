package com.peluqueria.auth;

import com.peluqueria.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limita el numero de intentos por IP a los endpoints de autenticacion mas sensibles
 * (login, alta de cuenta, recuperacion de contrasena), para dificultar ataques de fuerza
 * bruta o de spam de emails.
 *
 * Contador en memoria de ventana fija: vale para una sola instancia del backend (que es
 * como corre esta app en Render), no serviria tal cual con varias replicas detras de un
 * balanceador (habria que compartir el contador, ej. en Redis).
 */
@Component
public class AuthRateLimiter {

    private record Bucket(AtomicInteger count, Instant windowStart) {
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Registra un intento para la accion e IP dadas y lanza 429 si se supera el limite
     * de intentos permitidos dentro de la ventana de tiempo.
     */
    public void check(String action, String ip, int maxAttempts, long windowSeconds) {
        String key = action + ':' + ip;
        Instant now = Instant.now();

        Bucket bucket = buckets.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart().plusSeconds(windowSeconds).isBefore(now)) {
                return new Bucket(new AtomicInteger(1), now);
            }
            existing.count().incrementAndGet();
            return existing;
        });

        if (bucket.count().get() > maxAttempts) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos, espera unos minutos antes de volver a intentarlo");
        }
    }

    /** Purga cada 30 minutos las ventanas ya caducadas para no acumular memoria indefinidamente. */
    @Scheduled(fixedRate = 30 * 60 * 1000)
    void cleanup() {
        Instant cutoff = Instant.now().minusSeconds(3600);
        buckets.values().removeIf(bucket -> bucket.windowStart().isBefore(cutoff));
    }
}
