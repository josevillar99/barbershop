package com.peluqueria.worker.dto;

/** Trabajador cualificado y libre en una fecha/hora concreta, para elegirlo al confirmar una reserva. */
public record AvailableWorkerView(
        Long id,
        String name
) {
}
