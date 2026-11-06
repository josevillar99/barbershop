package com.peluqueria.booking.dto;

/** Franja bloqueada manualmente por el admin, con el motivo indicado (si lo hay). */
public record BlockedInterval(
        String startTime,
        String endTime,
        String reason
) {
}
