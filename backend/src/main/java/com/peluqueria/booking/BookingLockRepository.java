package com.peluqueria.booking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Acceso a datos de la fila de bloqueo (BookingLock) usada como punto de
 * sincronizacion para serializar reservas concurrentes.
 */
public interface BookingLockRepository extends JpaRepository<BookingLock, Long> {

    /** Obtiene y bloquea (PESSIMISTIC_WRITE) la fila de sincronizacion hasta que termine la transaccion. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BookingLock b where b.id = :id")
    Optional<BookingLock> lockById(@Param("id") Long id);
}
