package com.peluqueria.booking.dto;

import java.time.LocalDate;

/** Cita (reservada o cancelada) del historico del cliente autenticado, para su pagina de perfil. */
public record AppointmentHistoryView(
        Long id,
        LocalDate date,
        String startTime,
        String endTime,
        String treatmentName,
        String notes,
        String workerName,
        String workerColor,
        String status,
        boolean completed
) {
}
