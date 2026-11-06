package com.peluqueria.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

/** Datos recibidos para crear o actualizar un servicio: nombre, estado, precio y tramos de tiempo. */
public record TreatmentRequest(
        @NotBlank String name,
        boolean active,
        @NotNull @PositiveOrZero BigDecimal price,
        @NotEmpty List<SegmentView> segments
) {
}
