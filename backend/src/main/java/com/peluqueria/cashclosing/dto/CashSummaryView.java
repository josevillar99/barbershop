package com.peluqueria.cashclosing.dto;

import com.peluqueria.booking.dto.AdminAppointmentView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resumen de caja de un dia: totales cobrados por metodo de pago segun las citas marcadas como
 * hechas, y (si ya se ha cerrado ese dia) el efectivo contado fisicamente y el descuadre frente
 * al efectivo esperado.
 */
public record CashSummaryView(
        LocalDate date,
        BigDecimal efectivoTotal,
        BigDecimal tarjetaTotal,
        BigDecimal otroTotal,
        BigDecimal total,
        int appointmentCount,
        List<AdminAppointmentView> appointments,
        BigDecimal countedCash,
        BigDecimal difference
) {
}
