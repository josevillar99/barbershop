package com.peluqueria.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** Datos que envia el admin para reservar una cita en nombre de un cliente sin cuenta. */
public record AdminBookRequest(
        @NotEmpty List<Long> treatmentIds,
        @NotNull LocalDate date,
        @NotBlank String startTime,
        String notes,
        @NotBlank String clientName,
        @NotBlank String clientPhone,
        @NotNull Long workerId
) {
}
