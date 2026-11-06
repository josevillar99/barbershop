package com.peluqueria.worker.dto;

import com.peluqueria.booking.dto.OpeningHourView;

import java.util.List;

/** Vista de un trabajador para el panel de administracion y para el selector de trabajador al reservar. */
public record WorkerView(
        Long id,
        Long userId,
        String name,
        String email,
        boolean active,
        String color,
        List<Long> treatmentIds,
        List<OpeningHourView> weeklyHours
) {
}
