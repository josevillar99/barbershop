package com.peluqueria.booking.dto;

import com.peluqueria.booking.PaymentMethod;
import com.peluqueria.catalog.dto.SegmentView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Vista de una cita para el panel de administracion, incluyendo datos del cliente y quien la reservo. */
public record AdminAppointmentView(
        Long id,
        LocalDate date,
        String startTime,
        String endTime,
        String treatmentName,
        List<SegmentView> segments,
        String notes,
        String clientName,
        String clientPhone,
        String bookedByEmail,
        Long workerId,
        String workerName,
        String workerColor,
        boolean completed,
        BigDecimal expectedPrice,
        BigDecimal amountCharged,
        PaymentMethod paymentMethod
) {
}
