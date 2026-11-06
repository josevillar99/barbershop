package com.peluqueria.booking;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fila unica (id=1, sembrada por Flyway) usada como punto de sincronizacion
 * explicito: bajo READ COMMITTED, dos reservas concurrentes podrian
 * consultarse sin verse (write skew) ya que no hay ningun constraint de BD
 * que impida solapes de rango. Se bloquea con PESSIMISTIC_WRITE antes de
 * re-validar y guardar una reserva o un bloqueo administrativo.
 */
@Entity
@Table(name = "booking_lock")
@Getter
@Setter
@NoArgsConstructor
public class BookingLock {

    @Id
    private Long id;
}
