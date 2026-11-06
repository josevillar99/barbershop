package com.peluqueria.booking.dto;

/** Un tramo de horario de apertura, tal y como se muestra en el panel de administracion. */
public record OpeningHourView(
        int dayOfWeek,
        String startTime,
        String endTime
) {
}
