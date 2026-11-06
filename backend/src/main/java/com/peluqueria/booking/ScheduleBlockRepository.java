package com.peluqueria.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/** Acceso a datos de los bloqueos manuales de horario (ScheduleBlock). */
public interface ScheduleBlockRepository extends JpaRepository<ScheduleBlock, Long> {

    /** Bloqueos de horario existentes en un dia concreto, de cualquier trabajador o de todo el salon. */
    List<ScheduleBlock> findByDate(LocalDate date);

    /** Bloqueos que afectan a ese trabajador en un dia concreto: los suyos propios y los de todo el salon. */
    @Query("select b from ScheduleBlock b where b.date = :date and (b.worker.id = :workerId or b.worker is null)")
    List<ScheduleBlock> findByDateForWorker(@Param("date") LocalDate date, @Param("workerId") Long workerId);

    /** Bloqueos de horario dentro de un rango de fechas, ordenados cronologicamente. */
    List<ScheduleBlock> findByDateBetweenOrderByDateAscStartTimeAsc(LocalDate from, LocalDate to);
}
