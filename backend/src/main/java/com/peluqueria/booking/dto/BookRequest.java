package com.peluqueria.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** Datos que envia un cliente autenticado para reservar una cita propia. */
public record BookRequest(
        @NotEmpty List<Long> treatmentIds,
        @NotNull LocalDate date,
        @NotBlank String startTime,
        String notes,
        @NotNull Long workerId
) {
}
