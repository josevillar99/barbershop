package com.peluqueria.common;

import java.time.Instant;

/** Cuerpo de respuesta estandar para errores de la API, con marca de tiempo, codigo HTTP y mensaje. */
public record ApiError(Instant timestamp, int status, String message) {

    /** Crea un ApiError con la marca de tiempo actual. */
    public static ApiError of(int status, String message) {
        return new ApiError(Instant.now(), status, message);
    }
}
