package com.peluqueria.booking;

/** Intervalo de apertura del negocio, expresado en minutos desde medianoche. */
public record OpenInterval(int startMinutes, int endMinutes) {
}
