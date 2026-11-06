package com.peluqueria.stats;

import com.peluqueria.booking.AppointmentRepository;
import com.peluqueria.booking.AppointmentStatus;
import com.peluqueria.stats.dto.StatsResponse;
import com.peluqueria.stats.dto.StatsSummaryView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

/** Calcula las estadisticas de citas (totales, por trabajador y por servicio) para un rango de fechas. */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final AppointmentRepository appointmentRepository;

    @Transactional(readOnly = true)
    public StatsResponse getStats(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        long totalBooked = appointmentRepository.countByDateBetweenAndStatus(from, to, AppointmentStatus.BOOKED);
        long cancelledStatus = appointmentRepository.countByDateBetweenAndStatus(from, to, AppointmentStatus.CANCELLED);
        long completed = appointmentRepository.countCompleted(from, to);
        long completedEligible = appointmentRepository.countCompletedEligible(from, to, today, now);
        // Las citas pasadas sin marcar como hechas se cuentan como canceladas
        // (el cliente no se presento), junto con las canceladas de verdad.
        long noShow = appointmentRepository.countNoShow(from, to, today, now);
        long cancelled = cancelledStatus + noShow;
        long resolved = completed + cancelled;
        double cancellationRatePercent = resolved == 0 ? 0 : (cancelled * 100.0) / resolved;

        StatsSummaryView summary = new StatsSummaryView(totalBooked, completed, completedEligible, cancelled, cancellationRatePercent);
        return new StatsResponse(
                summary,
                appointmentRepository.countByWorker(from, to),
                appointmentRepository.countByTreatment(from, to));
    }
}
