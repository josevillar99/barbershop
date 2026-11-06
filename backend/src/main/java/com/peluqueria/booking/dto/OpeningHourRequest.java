package com.peluqueria.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Un tramo de horario tal y como lo envia el admin al guardar cambios desde el panel. */
public record OpeningHourRequest(
        @Min(1) @Max(7) int dayOfWeek,
        @NotBlank String startTime,
        @NotBlank String endTime
) {
}
