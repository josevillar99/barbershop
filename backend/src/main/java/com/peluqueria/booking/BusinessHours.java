package com.peluqueria.booking;

import com.peluqueria.booking.dto.OpeningHourView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Horario de apertura semanal de un trabajador (tabla business_hours, con
 * worker_id). Cada dia de la semana (1=lunes .. 7=domingo) puede tener cero,
 * uno o varios tramos abiertos; los intervalos se expresan en minutos desde
 * medianoche para poder razonar con duraciones de servicio arbitrarias. La
 * edicion del horario (alta/baja de tramos) vive en {@code worker.WorkerService}.
 */
@Service
@RequiredArgsConstructor
public class BusinessHours {

    private final OpeningHourRepository openingHourRepository;

    /** Tramos de apertura de ese trabajador en ese dia de la semana (vacio si ese dia no trabaja). */
    @Transactional(readOnly = true)
    public List<OpenInterval> intervalsFor(DayOfWeek dayOfWeek, Long workerId) {
        return openingHourRepository.findByWorkerIdAndDayOfWeekOrderByStartTimeAsc(workerId, dayOfWeek.getValue()).stream()
                .map(h -> new OpenInterval(toMinutes(h.getStartTime()), toMinutes(h.getEndTime())))
                .toList();
    }

    /**
     * Union de los horarios de todos los trabajadores activos, dia a dia, para
     * pintar el esqueleto del calendario (que dias/horas tienen sentido
     * mostrar). No representa la disponibilidad real de ningun trabajador en
     * concreto: eso lo calcula {@code AvailabilityService} por separado.
     */
    @Transactional(readOnly = true)
    public List<OpeningHourView> listUnionOfActiveWorkers() {
        Map<Integer, List<int[]>> byDay = new TreeMap<>();
        for (OpeningHour h : openingHourRepository.findByWorkerActiveTrue()) {
            byDay.computeIfAbsent(h.getDayOfWeek(), d -> new ArrayList<>())
                    .add(new int[]{toMinutes(h.getStartTime()), toMinutes(h.getEndTime())});
        }
        List<OpeningHourView> result = new ArrayList<>();
        for (Map.Entry<Integer, List<int[]>> entry : byDay.entrySet()) {
            for (int[] merged : mergeIntervals(entry.getValue())) {
                result.add(new OpeningHourView(entry.getKey(),
                        AvailabilityService.formatMinutes(merged[0]), AvailabilityService.formatMinutes(merged[1])));
            }
        }
        return result;
    }

    /** Fusiona intervalos de minutos solapados o contiguos en una lista ordenada y sin huecos redundantes. */
    private static List<int[]> mergeIntervals(List<int[]> intervals) {
        List<int[]> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparingInt(a -> a[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] interval : sorted) {
            if (!merged.isEmpty() && interval[0] <= merged.get(merged.size() - 1)[1]) {
                int[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], interval[1]);
            } else {
                merged.add(interval.clone());
            }
        }
        return merged;
    }

    private static int toMinutes(java.time.LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }
}
