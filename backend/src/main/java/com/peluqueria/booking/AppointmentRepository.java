package com.peluqueria.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Acceso a datos de las citas (Appointment): consultas de lectura por fecha
 * o cliente y actualizaciones atomicas de cancelacion.
 */
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** Citas con ese estado (reservada/cancelada) en un dia concreto. */
    List<Appointment> findByDateAndStatus(LocalDate date, AppointmentStatus status);

    /** Citas con ese estado en un dia concreto para un trabajador determinado. */
    List<Appointment> findByDateAndStatusAndWorkerId(LocalDate date, AppointmentStatus status, Long workerId);

    /** Citas con ese estado dentro de un rango de fechas, ordenadas cronologicamente. */
    List<Appointment> findByDateBetweenAndStatusOrderByDateAscStartTimeAsc(
            LocalDate from, LocalDate to, AppointmentStatus status);

    /** Citas de un cliente concreto con ese estado dentro de un rango de fechas, ordenadas cronologicamente. */
    List<Appointment> findByBookedByIdAndDateBetweenAndStatusOrderByDateAscStartTimeAsc(
            Long userId, LocalDate from, LocalDate to, AppointmentStatus status);

    /** Todo el historico de citas de un cliente (reservadas y canceladas), la mas reciente primero. */
    List<Appointment> findByBookedByIdOrderByDateDescStartTimeDesc(Long userId);

    /** Cuenta las citas con ese estado dentro de un rango de fechas (para estadisticas). */
    long countByDateBetweenAndStatus(LocalDate from, LocalDate to, AppointmentStatus status);

    /** Citas reservadas y marcadas como realizadas en un dia concreto, para el cierre de caja. */
    List<Appointment> findByDateAndStatusAndCompletedTrueOrderByStartTimeAsc(LocalDate date, AppointmentStatus status);

    /** Cuenta las citas reservadas y marcadas como realizadas dentro de un rango de fechas. */
    @Query("select count(a) from Appointment a where a.date between :from and :to "
            + "and a.status = com.peluqueria.booking.AppointmentStatus.BOOKED and a.completed = true")
    long countCompleted(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Cuenta las citas reservadas cuya fecha y hora de fin ya han pasado (no
     * solo el dia, tambien las de hoy que ya han terminado), dentro de un
     * rango de fechas: es el denominador de la proporcion de "hechas".
     */
    @Query("select count(a) from Appointment a where a.date between :from and :to "
            + "and a.status = com.peluqueria.booking.AppointmentStatus.BOOKED "
            + "and (a.date < :today or (a.date = :today and a.endTime <= :nowTime))")
    long countCompletedEligible(@Param("from") LocalDate from, @Param("to") LocalDate to,
            @Param("today") LocalDate today, @Param("nowTime") java.time.LocalTime nowTime);

    /**
     * Cuenta las citas reservadas ya pasadas (fecha y hora de fin) y sin
     * marcar como hechas: el cliente no se presento, dentro de un rango de
     * fechas.
     */
    @Query("select count(a) from Appointment a where a.date between :from and :to "
            + "and a.status = com.peluqueria.booking.AppointmentStatus.BOOKED and a.completed = false "
            + "and (a.date < :today or (a.date = :today and a.endTime <= :nowTime))")
    long countNoShow(@Param("from") LocalDate from, @Param("to") LocalDate to,
            @Param("today") LocalDate today, @Param("nowTime") java.time.LocalTime nowTime);

    /** Numero de citas reservadas y cuantas de ellas realizadas, agrupado por trabajador, dentro de un rango de fechas. */
    @Query("select new com.peluqueria.stats.dto.WorkerStatsView(w.id, u.name, w.color, count(a), "
            + "sum(case when a.completed = true then 1L else 0L end)) "
            + "from Appointment a join a.worker w join w.user u "
            + "where a.date between :from and :to and a.status = com.peluqueria.booking.AppointmentStatus.BOOKED "
            + "group by w.id, u.name, w.color order by count(a) desc")
    List<com.peluqueria.stats.dto.WorkerStatsView> countByWorker(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Numero de citas reservadas agrupado por servicio (nombre combinado tal y como se reservo), dentro de un rango de fechas. */
    @Query("select new com.peluqueria.stats.dto.TreatmentStatsView(a.treatmentName, count(a)) "
            + "from Appointment a "
            + "where a.date between :from and :to and a.status = com.peluqueria.booking.AppointmentStatus.BOOKED "
            + "group by a.treatmentName order by count(a) desc")
    List<com.peluqueria.stats.dto.TreatmentStatsView> countByTreatment(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // flushAutomatically: sin esto, cambios pendientes sin volcar (p.ej. los
    // tramos de una cita recien creada en la misma transaccion) se pierden en
    // silencio al limpiar el contexto de persistencia con clearAutomatically.
    /** Cancela una cita solo si pertenece al usuario indicado y sigue reservada; devuelve cuantas filas cambio. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Appointment a SET a.status = com.peluqueria.booking.AppointmentStatus.CANCELLED " +
            "WHERE a.id = :id AND a.status = com.peluqueria.booking.AppointmentStatus.BOOKED " +
            "AND a.bookedBy.id = :userId")
    int cancelOwnBooking(@Param("id") Long id, @Param("userId") Long userId);

    /** Cancela cualquier cita que siga reservada, sin comprobar el propietario; devuelve cuantas filas cambio. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Appointment a SET a.status = com.peluqueria.booking.AppointmentStatus.CANCELLED " +
            "WHERE a.id = :id AND a.status = com.peluqueria.booking.AppointmentStatus.BOOKED")
    int cancelAnyBooking(@Param("id") Long id);

    /**
     * Desvincula las citas de un usuario (deja bookedBy a NULL) antes de
     * borrarlo, para poder eliminar la cuenta sin perder el historico de citas
     * (que ya guarda el nombre/telefono del cliente de forma independiente).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Appointment a SET a.bookedBy = null WHERE a.bookedBy.id = :userId")
    void detachBookedBy(@Param("userId") Long userId);

    /**
     * Desvincula las citas (pasadas o futuras) de un trabajador (deja worker a
     * NULL) antes de eliminar su perfil, sin borrar el historico de citas.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Appointment a SET a.worker = null WHERE a.worker.id = :workerId")
    void detachWorker(@Param("workerId") Long workerId);
}
