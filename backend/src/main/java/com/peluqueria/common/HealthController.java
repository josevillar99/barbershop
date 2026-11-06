package com.peluqueria.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Endpoint publico de comprobacion de salud, usado por la plataforma de despliegue (ej. Render). */
@RestController
public class HealthController {

    /** Responde 200 si el backend esta arriba y respondiendo peticiones. */
    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
