package com.peluqueria.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

/** Acceso a datos de los tramos de horario de apertura configurados, por trabajador. */
public interface OpeningHourRepository extends JpaRepository<OpeningHour, Long> {

    /** Tramos de un dia de la semana concreto para ese trabajador, ordenados de mas temprano a mas tarde. */
    List<OpeningHour> findByWorkerIdAndDayOfWeekOrderByStartTimeAsc(Long workerId, int dayOfWeek);

    /** Todo el horario semanal de un trabajador, ordenado por dia y hora. */
    List<OpeningHour> findByWorkerIdOrderByDayOfWeekAscStartTimeAsc(Long workerId);

    /** Filas plantilla heredadas del antiguo horario unico del salon (sin trabajador asignado). */
    List<OpeningHour> findByWorkerIsNull();

    /** Todo el horario de los trabajadores activos, para calcular el esqueleto de dias/horas abiertos del calendario. */
    List<OpeningHour> findByWorkerActiveTrue();

    /** Elimina todo el horario semanal actual de un trabajador, antes de sustituirlo por uno nuevo. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    void deleteByWorkerId(Long workerId);
}
