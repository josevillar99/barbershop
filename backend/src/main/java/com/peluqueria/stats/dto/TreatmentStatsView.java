package com.peluqueria.stats.dto;

/** Numero de citas reservadas para un servicio (o combinacion de servicios) dentro del rango de fechas consultado. */
public record TreatmentStatsView(
        String treatmentName,
        long appointmentCount
) {
}
