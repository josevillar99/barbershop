package com.peluqueria.booking.dto;

import java.time.LocalDate;
import java.util.List;

/** Franjas horarias ya ocupadas (por citas, sin datos de cliente ni servicio) y bloqueadas por el admin en un dia concreto. */
public record OccupiedDayView(
        LocalDate date,
        List<OccupiedInterval> occupied,
        List<BlockedInterval> blocked
) {
}
