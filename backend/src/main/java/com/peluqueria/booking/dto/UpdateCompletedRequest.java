package com.peluqueria.booking.dto;

import com.peluqueria.booking.PaymentMethod;

import java.math.BigDecimal;

/**
 * Marca si una cita se llego a realizar o no, tal y como se guarda desde el panel de administracion.
 * Si {@code completed} es true, {@code amountCharged} y {@code paymentMethod} son obligatorios.
 */
public record UpdateCompletedRequest(
        boolean completed,
        BigDecimal amountCharged,
        PaymentMethod paymentMethod
) {
}
