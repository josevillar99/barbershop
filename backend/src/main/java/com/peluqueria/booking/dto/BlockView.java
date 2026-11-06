package com.peluqueria.booking.dto;

import java.time.LocalDate;

/** Vista de salida de un bloqueo de horario, con su rango, el motivo y a quien afecta (null = todo el salon). */
public record BlockView(
        Long id,
        LocalDate date,
        String startTime,
        String endTime,
        String reason,
        Long workerId,
        String workerName
) {
}
