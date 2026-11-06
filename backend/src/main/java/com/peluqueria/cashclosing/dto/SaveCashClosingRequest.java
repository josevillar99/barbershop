package com.peluqueria.cashclosing.dto;

import java.math.BigDecimal;

/** Efectivo contado fisicamente al cerrar la caja de un dia. */
public record SaveCashClosingRequest(
        BigDecimal countedCash
) {
}
