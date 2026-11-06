package com.peluqueria.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Extrae la IP real del cliente de una peticion: en Docker Compose y en Render las
 * peticiones llegan a traves de un proxy (nginx / el balanceador de Render), que pone la
 * IP original en X-Forwarded-For; sin ese header (ej. en local sin proxy) se usa la IP de
 * conexion directa.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
