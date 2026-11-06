package com.peluqueria.stats.dto;

/** Citas reservadas (y cuantas realizadas) de un trabajador dentro del rango de fechas consultado. */
public record WorkerStatsView(
        Long workerId,
        String workerName,
        String workerColor,
        long appointmentCount,
        long completedCount
) {
}
