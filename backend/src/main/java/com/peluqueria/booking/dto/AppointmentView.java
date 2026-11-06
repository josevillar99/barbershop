package com.peluqueria.booking.dto;

import com.peluqueria.catalog.dto.SegmentView;

import java.time.LocalDate;
import java.util.List;

/** Vista publica de una cita, tal como la ve el cliente que consulta sus reservas. */
public record AppointmentView(
        Long id,
        LocalDate date,
        String startTime,
        String endTime,
        String treatmentName,
        List<SegmentView> segments,
        String notes,
        boolean mine,
        Long workerId,
        String workerName,
        String workerColor
) {
}
