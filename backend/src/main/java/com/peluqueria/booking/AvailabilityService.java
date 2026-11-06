package com.peluqueria.booking;

import com.peluqueria.booking.dto.AvailabilityDayView;
import com.peluqueria.booking.dto.BlockedInterval;
import com.peluqueria.booking.dto.OccupiedDayView;
import com.peluqueria.booking.dto.OccupiedInterval;
import com.peluqueria.catalog.Segment;
import com.peluqueria.catalog.Treatment;
import com.peluqueria.catalog.TreatmentService;
import com.peluqueria.common.ApiException;
import com.peluqueria.config.AppProperties;
import com.peluqueria.worker.Worker;
import com.peluqueria.worker.dto.AvailableWorkerView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Motor de disponibilidad: calcula, para un rango de fechas y una seleccion
 * de servicios, que horas de inicio son reservables (agregando la agenda de
 * todos los trabajadores cualificados) y que trabajadores concretos estan
 * libres en una fecha/hora exacta, combinando el horario de cada trabajador,
 * las citas ya reservadas y los bloqueos administrativos (suyos o de todo el salon).
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final AppointmentRepository appointmentRepository;
    private final ScheduleBlockRepository scheduleBlockRepository;
    private final BusinessHours businessHours;
    private final TreatmentService treatmentService;
    private final AppProperties appProperties;

    /**
     * Calcula, dia a dia dentro del rango, las horas de inicio validas para
     * la combinacion de servicios pedida: una hora aparece si algun
     * trabajador cualificado para todos esos servicios esta libre entonces.
     */
    @Transactional(readOnly = true)
    public List<AvailabilityDayView> availability(List<Long> treatmentIds, LocalDate from, LocalDate to) {
        List<Treatment> treatments = treatmentIds.stream().map(treatmentService::getTreatment).toList();
        List<Segment> combinedSegments = SegmentTimeline.concat(treatments);
        int minGap = treatmentService.minActiveDurationMinutes();
        int step = appProperties.booking().slotStepMinutes();
        int tolerableGap = appProperties.booking().tolerableGapMinutes();
        List<Worker> qualified = qualifiedWorkers(treatments);

        LocalDate today = LocalDate.now();
        int nowMinutes = toMinutes(LocalTime.now());

        List<AvailabilityDayView> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            boolean isToday = date.equals(today);
            TreeSet<String> times = new TreeSet<>();
            for (Worker worker : qualified) {
                List<OpenInterval> intervals = businessHours.intervalsFor(date.getDayOfWeek(), worker.getId());
                if (intervals.isEmpty()) {
                    continue;
                }
                List<int[]> occupied = occupiedWorkIntervals(date, worker.getId());
                for (OpenInterval interval : intervals) {
                    for (int t : candidateStarts(interval, occupied, step)) {
                        if (isToday && t <= nowMinutes) {
                            continue;
                        }
                        List<int[]> candidateWork = SegmentTimeline.workIntervals(t, combinedSegments);
                        if (AvailabilityValidator.isValidCandidate(intervals, occupied, candidateWork, minGap, tolerableGap)) {
                            times.add(formatMinutes(t));
                        }
                    }
                }
            }
            result.add(new AvailabilityDayView(date, new ArrayList<>(times)));
        }
        return result;
    }

    /**
     * Trabajadores cualificados para los servicios elegidos y libres exactamente
     * en esa fecha/hora, para que el cliente elija con quien quiere la cita.
     */
    @Transactional(readOnly = true)
    public List<AvailableWorkerView> availableWorkers(List<Long> treatmentIds, LocalDate date, String startTimeText) {
        List<Treatment> treatments = treatmentIds.stream().map(treatmentService::getTreatment).toList();
        List<Segment> combinedSegments = SegmentTimeline.concat(treatments);
        int minGap = treatmentService.minActiveDurationMinutes();
        int tolerableGap = appProperties.booking().tolerableGapMinutes();
        int t = toMinutes(parseTime(startTimeText));
        List<int[]> candidateWork = SegmentTimeline.workIntervals(t, combinedSegments);

        List<AvailableWorkerView> free = new ArrayList<>();
        for (Worker worker : qualifiedWorkers(treatments)) {
            List<OpenInterval> intervals = businessHours.intervalsFor(date.getDayOfWeek(), worker.getId());
            if (intervals.isEmpty()) {
                continue;
            }
            List<int[]> occupied = occupiedWorkIntervals(date, worker.getId());
            if (AvailabilityValidator.isValidCandidate(intervals, occupied, candidateWork, minGap, tolerableGap)) {
                free.add(new AvailableWorkerView(worker.getId(), worker.getUser().getName()));
            }
        }
        return free;
    }

    /** Interseccion de los trabajadores activos cualificados para TODOS los servicios indicados. */
    List<Worker> qualifiedWorkers(List<Treatment> treatments) {
        Set<Worker> qualified = null;
        for (Treatment treatment : treatments) {
            if (qualified == null) {
                qualified = new HashSet<>(treatment.getWorkers());
            } else {
                qualified.retainAll(treatment.getWorkers());
            }
        }
        if (qualified == null) {
            return List.of();
        }
        return qualified.stream().filter(Worker::isActive).toList();
    }

    /**
     * Franjas ocupadas sin depender de una seleccion de tratamiento ni de
     * trabajador, para pintarlas en el calendario aunque el usuario aun no
     * haya elegido servicio (vision agregada de toda la agenda, solo a modo
     * de vistazo previo).
     */
    @Transactional(readOnly = true)
    public List<OccupiedDayView> occupied(LocalDate from, LocalDate to) {
        List<OccupiedDayView> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            result.add(new OccupiedDayView(date, occupiedIntervals(date), blockedIntervals(date)));
        }
        return result;
    }

    /** Franjas bloqueadas manualmente ese dia (de cualquier trabajador o de todo el salon), con el motivo indicado (si lo hay). */
    private List<BlockedInterval> blockedIntervals(LocalDate date) {
        return scheduleBlockRepository.findByDate(date).stream()
                .map(b -> new BlockedInterval(formatMinutes(toMinutes(b.getStartTime())), formatMinutes(toMinutes(b.getEndTime())), b.getReason()))
                .toList();
    }

    /**
     * Franjas ya reservadas ese dia por cualquier trabajador, solo con
     * horario (sin cliente ni servicio), a modo de vistazo previo antes de
     * elegir servicio. Usa solo los tramos de trabajo de cada cita (no el
     * rango completo), para que el hueco de espera de servicios como Tinte o
     * Mechas no aparezca ocupado, ya que ese rato si se puede usar para
     * atender a otro cliente (con el mismo u otro trabajador).
     */
    private List<OccupiedInterval> occupiedIntervals(LocalDate date) {
        List<OccupiedInterval> result = new ArrayList<>();
        for (Appointment appointment : appointmentRepository.findByDateAndStatus(date, AppointmentStatus.BOOKED)) {
            Worker worker = appointment.getWorker();
            String workerName = worker != null ? worker.getUser().getName() : null;
            String workerColor = worker != null ? worker.getColor() : null;
            for (int[] work : SegmentTimeline.workIntervals(toMinutes(appointment.getStartTime()), appointment.getSegments())) {
                result.add(new OccupiedInterval(formatMinutes(work[0]), formatMinutes(work[1]), workerName, workerColor));
            }
        }
        return result;
    }

    /**
     * Candidatos de inicio dentro de un intervalo abierto: la rejilla de paso
     * fijo, mas el instante exacto en que termina cada tramo ya ocupado (para
     * no perder el hueco de "empezar justo a continuacion" cuando ese instante
     * no cae en la rejilla, p.ej. una Barba de 20 min terminando a las 9:20
     * con un paso de 15 min).
     */
    private List<Integer> candidateStarts(OpenInterval interval, List<int[]> occupied, int step) {
        TreeSet<Integer> candidates = new TreeSet<>();
        for (int t = interval.startMinutes(); t < interval.endMinutes(); t += step) {
            candidates.add(t);
        }
        for (int[] block : occupied) {
            if (block[1] > interval.startMinutes() && block[1] < interval.endMinutes()) {
                candidates.add(block[1]);
            }
        }
        return new ArrayList<>(candidates);
    }

    /**
     * Reune, en minutos del dia, todos los tramos ocupados de esa fecha para
     * ese trabajador: sus propios tramos de trabajo reservados mas los
     * bloqueos que le afectan (suyos o de todo el salon).
     */
    List<int[]> occupiedWorkIntervals(LocalDate date, Long workerId) {
        List<int[]> occupied = new ArrayList<>();
        for (Appointment appointment : appointmentRepository.findByDateAndStatusAndWorkerId(date, AppointmentStatus.BOOKED, workerId)) {
            occupied.addAll(SegmentTimeline.workIntervals(toMinutes(appointment.getStartTime()), appointment.getSegments()));
        }
        for (ScheduleBlock block : scheduleBlockRepository.findByDateForWorker(date, workerId)) {
            occupied.add(new int[]{toMinutes(block.getStartTime()), toMinutes(block.getEndTime())});
        }
        return occupied;
    }

    /**
     * Reune los tramos ocupados de todo un dia para TODOS los trabajadores
     * activos indicados, usado al crear un bloqueo de todo el salon (debe
     * comprobar que no se solapa con ninguna cita de ningun trabajador).
     */
    List<int[]> occupiedWorkIntervalsForAnyWorker(LocalDate date, List<Worker> activeWorkers) {
        List<int[]> occupied = new ArrayList<>();
        for (Worker worker : activeWorkers) {
            occupied.addAll(occupiedWorkIntervals(date, worker.getId()));
        }
        return occupied;
    }

    /** Convierte un texto de hora (ej. "09:30") a LocalTime, lanzando un error de negocio si no es valido. */
    private LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Hora invalida: " + text);
        }
    }

    /** Convierte una hora del dia a minutos desde medianoche, para poder compararla y sumarla facilmente. */
    static int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    /** Formatea unos minutos desde medianoche como texto de hora "HH:mm" para mostrar al usuario. */
    static String formatMinutes(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }
}
