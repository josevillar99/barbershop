package com.peluqueria.stats.dto;

/**
 * Resumen global de citas dentro del rango de fechas consultado.
 * completedEligible es el numero de citas que ya han pasado (fecha+hora), el
 * denominador natural para mostrar "hechas" como una proporcion (ej. 8/10);
 * las citas de hoy que aun no han llegado a su hora no cuentan todavia.
 * cancelled incluye tanto las canceladas de verdad como las citas pasadas
 * que se quedaron sin marcar como hechas (el cliente no se presento).
 */
public record StatsSummaryView(
        long totalBooked,
        long completed,
        long completedEligible,
        long cancelled,
        double cancellationRatePercent
) {
}
