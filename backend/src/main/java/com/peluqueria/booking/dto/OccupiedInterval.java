package com.peluqueria.booking.dto;

/** Franja horaria ocupada, con el trabajador que la ocupa (si la cita tiene uno asignado). */
public record OccupiedInterval(
        String startTime,
        String endTime,
        String workerName,
        String workerColor
) {
}
