package com.peluqueria.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Datos que envia el admin para crear un nuevo bloqueo manual de horario. workerId nulo = bloqueo de todo el salon. */
public record CreateBlockRequest(
        @NotNull LocalDate date,
        @NotBlank String startTime,
        @NotBlank String endTime,
        String reason,
        Long workerId
) {
}
