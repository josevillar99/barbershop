package com.peluqueria.booking.dto;

import java.time.LocalDate;
import java.util.List;

/** Horas de inicio disponibles para reservar en un dia concreto. */
public record AvailabilityDayView(
        LocalDate date,
        List<String> times
) {
}
