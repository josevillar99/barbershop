package com.peluqueria.booking;

import com.peluqueria.booking.dto.AdminAppointmentView;
import com.peluqueria.booking.dto.AppointmentHistoryView;
import com.peluqueria.booking.dto.AppointmentView;
import com.peluqueria.booking.dto.BlockView;
import com.peluqueria.catalog.Segment;
import com.peluqueria.catalog.Treatment;
import com.peluqueria.catalog.TreatmentService;
import com.peluqueria.catalog.dto.SegmentView;
import com.peluqueria.common.ApiException;
import com.peluqueria.config.AppProperties;
import com.peluqueria.user.User;
import com.peluqueria.user.UserRepository;
import com.peluqueria.worker.Worker;
import com.peluqueria.worker.WorkerRepository;
import com.peluqueria.worker.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Logica de negocio de reservas: crear, listar y cancelar citas, y crear o
 * eliminar bloqueos de horario, siempre re-validando la disponibilidad del
 * trabajador elegido bajo el bloqueo de sincronizacion para evitar reservas
 * duplicadas o solapadas.
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final Long LOCK_ID = 1L;

    private final AppointmentRepository appointmentRepository;
    private final ScheduleBlockRepository scheduleBlockRepository;
    private final BookingLockRepository bookingLockRepository;
    private final BusinessHours businessHours;
    private final TreatmentService treatmentService;
    private final AvailabilityService availabilityService;
    private final UserRepository userRepository;
    private final WorkerService workerService;
    private final WorkerRepository workerRepository;
    private final AppProperties appProperties;

    /** Reserva una cita para el usuario autenticado con el trabajador elegido, tras validar y bloquear la agenda. */
    @Transactional
    public AppointmentView book(List<Long> treatmentIds, LocalDate date, String startTimeText, String notes,
                                 Long workerId, String userEmail) {
        acquireLock();

        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));

        Appointment appointment = buildValidatedAppointment(treatmentIds, date, startTimeText, notes, workerId);
        appointment.setBookedBy(user);
        appointment.setClientName(user.getName());
        appointment.setClientPhone(user.getPhone());

        return toView(appointmentRepository.save(appointment), userEmail);
    }

    /**
     * Reserva creada por el admin a nombre de un cliente sin cuenta (llamada
     * de telefono, persona que acude al local...). No queda ligada a ningun
     * User, solo al nombre/telefono que indique el admin.
     */
    @Transactional
    public AdminAppointmentView adminBook(List<Long> treatmentIds, LocalDate date, String startTimeText, String notes,
                                           String clientName, String clientPhone, Long workerId) {
        acquireLock();

        Appointment appointment = buildValidatedAppointment(treatmentIds, date, startTimeText, notes, workerId);
        appointment.setClientName(clientName);
        appointment.setClientPhone(clientPhone);

        return toAdminView(appointmentRepository.save(appointment));
    }

    /**
     * Valida el candidato (servicios activos, fecha dentro de horizonte, hora
     * no pasada, trabajador cualificado y libre) y construye la cita
     * combinada, sin fijar todavia quien la reservo (lo hace cada caller:
     * cliente propio o admin).
     */
    private Appointment buildValidatedAppointment(List<Long> treatmentIds, LocalDate date, String startTimeText,
                                                    String notes, Long workerId) {
        List<Treatment> treatments = treatmentIds.stream().map(treatmentService::getTreatment).toList();
        if (treatments.stream().anyMatch(t -> !t.isActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Alguno de esos servicios ya no esta disponible");
        }

        Worker worker = workerService.getActiveWorker(workerId);
        if (treatments.stream().anyMatch(t -> !t.getWorkers().contains(worker))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ese trabajador no puede realizar alguno de esos servicios");
        }

        LocalDate horizonLimit = LocalDate.now().plusDays(appProperties.booking().horizonDays());
        if (date.isAfter(horizonLimit) || date.isBefore(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Esa fecha no esta dentro del horizonte de reservas");
        }

        LocalTime startTime = parseTime(startTimeText);
        if (date.equals(LocalDate.now()) && !startTime.isAfter(LocalTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Esa hora ya ha pasado");
        }

        List<Segment> combinedSegments = SegmentTimeline.concat(treatments);
        List<OpenInterval> intervals = businessHours.intervalsFor(date.getDayOfWeek(), worker.getId());
        int minGap = treatmentService.minActiveDurationMinutes();
        int tolerableGap = appProperties.booking().tolerableGapMinutes();
        List<int[]> occupied = availabilityService.occupiedWorkIntervals(date, worker.getId());
        List<int[]> candidateWork = SegmentTimeline.workIntervals(
                AvailabilityService.toMinutes(startTime), combinedSegments);

        if (!AvailabilityValidator.isValidCandidate(intervals, occupied, candidateWork, minGap, tolerableGap)) {
            throw new ApiException(HttpStatus.CONFLICT, "Esa hora ya no esta disponible");
        }

        int totalMinutes = SegmentTimeline.totalMinutes(combinedSegments);
        String combinedName = treatments.stream().map(Treatment::getName).reduce((a, b) -> a + " + " + b).orElse("");
        BigDecimal expectedPrice = treatments.stream().map(Treatment::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Appointment.builder()
                .date(date)
                .startTime(startTime)
                .endTime(startTime.plusMinutes(totalMinutes))
                .treatmentId(treatments.get(0).getId())
                .treatmentName(combinedName)
                .notes(notes)
                .segments(copySegments(combinedSegments))
                .status(AppointmentStatus.BOOKED)
                .worker(worker)
                .bookedAt(Instant.now())
                .expectedPrice(expectedPrice)
                .build();
    }

    /** Lista las citas reservadas del usuario indicado dentro de un rango de fechas. */
    @Transactional(readOnly = true)
    public List<AppointmentView> listOwn(LocalDate from, LocalDate to, String userEmail) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        return appointmentRepository
                .findByBookedByIdAndDateBetweenAndStatusOrderByDateAscStartTimeAsc(
                        user.getId(), from, to, AppointmentStatus.BOOKED)
                .stream()
                .map(a -> toView(a, userEmail))
                .toList();
    }

    /** Historico completo (reservadas y canceladas) de citas del usuario indicado, la mas reciente primero. */
    @Transactional(readOnly = true)
    public List<AppointmentHistoryView> history(String userEmail) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        return appointmentRepository.findByBookedByIdOrderByDateDescStartTimeDesc(user.getId()).stream()
                .map(this::toHistoryView)
                .toList();
    }

    /** Lista todas las citas reservadas de todos los clientes dentro de un rango de fechas, para el admin. */
    @Transactional(readOnly = true)
    public List<AdminAppointmentView> listForAdmin(LocalDate from, LocalDate to) {
        return appointmentRepository
                .findByDateBetweenAndStatusOrderByDateAscStartTimeAsc(from, to, AppointmentStatus.BOOKED)
                .stream()
                .map(this::toAdminView)
                .toList();
    }

    /** Lista los bloqueos de horario existentes dentro de un rango de fechas. */
    @Transactional(readOnly = true)
    public List<BlockView> listBlocks(LocalDate from, LocalDate to) {
        return scheduleBlockRepository.findByDateBetweenOrderByDateAscStartTimeAsc(from, to).stream()
                .map(this::toBlockView)
                .toList();
    }

    /** Cancela una cita propia del usuario indicado; falla si no es suya, ya no esta activa o ya ha pasado. */
    @Transactional
    public void cancelOwn(Long appointmentId, String userEmail) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
        rejectIfPast(appointmentId);
        int updated = appointmentRepository.cancelOwnBooking(appointmentId, user.getId());
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "No puedes cancelar esa cita");
        }
    }

    /** Cancela cualquier cita activa por su id, sin comprobar el propietario (uso administrativo); falla si ya ha pasado. */
    @Transactional
    public void adminCancel(Long appointmentId) {
        rejectIfPast(appointmentId);
        int updated = appointmentRepository.cancelAnyBooking(appointmentId);
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Esa cita no tiene una reserva activa");
        }
    }

    /** Comprueba que la cita indicada no sea de una fecha ya pasada, antes de permitir cancelarla. */
    private void rejectIfPast(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cita no encontrada"));
        if (appointment.getDate().isBefore(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se puede cancelar una cita que ya ha pasado");
        }
    }

    /**
     * Marca (o desmarca) si una cita ya pasada se llego a realizar, para uso administrativo y
     * futuras estadisticas. Al marcarla como hecha son obligatorios el importe cobrado y el
     * metodo de pago; al desmarcarla se limpian (no se ha cobrado nada).
     */
    @Transactional
    public AdminAppointmentView setCompleted(Long appointmentId, boolean completed, BigDecimal amountCharged,
                                              PaymentMethod paymentMethod) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cita no encontrada"));
        appointment.setCompleted(completed);
        if (completed) {
            if (amountCharged == null || amountCharged.compareTo(BigDecimal.ZERO) < 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Indica el importe cobrado");
            }
            if (paymentMethod == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Indica el metodo de pago");
            }
            appointment.setAmountCharged(amountCharged);
            appointment.setPaymentMethod(paymentMethod);
        } else {
            appointment.setAmountCharged(null);
            appointment.setPaymentMethod(null);
        }
        return toAdminView(appointment);
    }

    /** Lista las citas completadas (hechas) de una fecha, para el cierre de caja diario. */
    @Transactional(readOnly = true)
    public List<AdminAppointmentView> listCompletedForCashClosing(LocalDate date) {
        return appointmentRepository.findByDateAndStatusAndCompletedTrueOrderByStartTimeAsc(date, AppointmentStatus.BOOKED)
                .stream()
                .map(this::toAdminView)
                .toList();
    }

    /**
     * Crea un bloqueo administrativo de horario, tras comprobar que no se
     * solapa con reservas o bloqueos existentes. Si {@code workerId} es nulo,
     * el bloqueo es de todo el salon y se comprueba contra todos los
     * trabajadores activos; si no, solo contra ese trabajador.
     */
    @Transactional
    public BlockView adminCreateBlock(LocalDate date, String startTimeText, String endTimeText, String reason, Long workerId) {
        acquireLock();

        LocalTime start = parseTime(startTimeText);
        LocalTime end = parseTime(endTimeText);
        if (!start.isBefore(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La hora de inicio debe ser anterior a la de fin");
        }

        Worker worker = workerId != null ? workerService.getActiveWorker(workerId) : null;
        List<int[]> occupied = worker != null
                ? availabilityService.occupiedWorkIntervals(date, worker.getId())
                : availabilityService.occupiedWorkIntervalsForAnyWorker(date, workerRepository.findByActiveTrueOrderByUserNameAsc());

        int startMinutes = AvailabilityService.toMinutes(start);
        int endMinutes = AvailabilityService.toMinutes(end);
        boolean overlapsBooking = occupied.stream()
                .anyMatch(o -> startMinutes < o[1] && o[0] < endMinutes);
        if (overlapsBooking) {
            throw new ApiException(HttpStatus.CONFLICT, "Ese rango se solapa con una reserva o bloqueo existente");
        }

        ScheduleBlock block = ScheduleBlock.builder()
                .date(date)
                .startTime(start)
                .endTime(end)
                .reason(reason)
                .worker(worker)
                .build();
        return toBlockView(scheduleBlockRepository.save(block));
    }

    /** Elimina un bloqueo de horario existente por su id; falla si no existe. */
    @Transactional
    public void adminDeleteBlock(Long blockId) {
        if (!scheduleBlockRepository.existsById(blockId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Bloqueo no encontrado");
        }
        scheduleBlockRepository.deleteById(blockId);
    }

    /** Adquiere el bloqueo pesimista de la agenda para serializar la operacion frente a otras concurrentes. */
    private void acquireLock() {
        bookingLockRepository.lockById(LOCK_ID)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo bloquear la agenda"));
    }

    /** Convierte un texto de hora (ej. "09:30") a LocalTime, lanzando un error de negocio si no es valido. */
    private LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Hora invalida: " + text);
        }
    }

    /** Copia en profundidad la lista de tramos, para que la cita guardada no comparta instancias con el catalogo. */
    private List<Segment> copySegments(List<Segment> segments) {
        List<Segment> copy = new ArrayList<>();
        for (Segment segment : segments) {
            copy.add(new Segment(segment.getType(), segment.getMinutes()));
        }
        return copy;
    }

    /** Convierte los tramos de una cita a su representacion de salida (DTO) para la API. */
    private List<SegmentView> toSegmentViews(List<Segment> segments) {
        return segments.stream().map(s -> new SegmentView(s.getType(), s.getMinutes())).toList();
    }

    /** Convierte una cita a la vista publica que ve el cliente, marcando si es suya o no. */
    private AppointmentView toView(Appointment appointment, String currentUserEmail) {
        boolean mine = appointment.getBookedBy() != null
                && appointment.getBookedBy().getEmail().equalsIgnoreCase(currentUserEmail);
        Worker worker = appointment.getWorker();
        return new AppointmentView(
                appointment.getId(),
                appointment.getDate(),
                AvailabilityService.formatMinutes(AvailabilityService.toMinutes(appointment.getStartTime())),
                AvailabilityService.formatMinutes(AvailabilityService.toMinutes(appointment.getEndTime())),
                appointment.getTreatmentName(),
                toSegmentViews(appointment.getSegments()),
                appointment.getNotes(),
                mine,
                worker != null ? worker.getId() : null,
                worker != null ? worker.getUser().getName() : null,
                worker != null ? worker.getColor() : null);
    }

    /** Convierte una cita a la vista de administracion, incluyendo datos del cliente y quien la reservo. */
    private AdminAppointmentView toAdminView(Appointment appointment) {
        String bookedByEmail = appointment.getBookedBy() != null ? appointment.getBookedBy().getEmail() : null;
        Worker worker = appointment.getWorker();
        return new AdminAppointmentView(
                appointment.getId(),
                appointment.getDate(),
                AvailabilityService.formatMinutes(AvailabilityService.toMinutes(appointment.getStartTime())),
                AvailabilityService.formatMinutes(AvailabilityService.toMinutes(appointment.getEndTime())),
                appointment.getTreatmentName(),
                toSegmentViews(appointment.getSegments()),
                appointment.getNotes(),
                appointment.getClientName(),
                appointment.getClientPhone(),
                bookedByEmail,
                worker != null ? worker.getId() : null,
                worker != null ? worker.getUser().getName() : null,
                worker != null ? worker.getColor() : null,
                appointment.isCompleted(),
                appointment.getExpectedPrice(),
                appointment.getAmountCharged(),
                appointment.getPaymentMethod());
    }

    /** Convierte una cita a la vista de historico que ve el cliente en su perfil, incluyendo las canceladas. */
    private AppointmentHistoryView toHistoryView(Appointment appointment) {
        Worker worker = appointment.getWorker();
        return new AppointmentHistoryView(
                appointment.getId(),
                appointment.getDate(),
                AvailabilityService.formatMinutes(AvailabilityService.toMinutes(appointment.getStartTime())),
                AvailabilityService.formatMinutes(AvailabilityService.toMinutes(appointment.getEndTime())),
                appointment.getTreatmentName(),
                appointment.getNotes(),
                worker != null ? worker.getUser().getName() : null,
                worker != null ? worker.getColor() : null,
                appointment.getStatus().name(),
                appointment.isCompleted());
    }

    /** Convierte un bloqueo de horario a su representacion de salida (DTO) para la API. */
    private BlockView toBlockView(ScheduleBlock block) {
        Worker worker = block.getWorker();
        return new BlockView(
                block.getId(),
                block.getDate(),
                AvailabilityService.formatMinutes(AvailabilityService.toMinutes(block.getStartTime())),
                AvailabilityService.formatMinutes(AvailabilityService.toMinutes(block.getEndTime())),
                block.getReason(),
                worker != null ? worker.getId() : null,
                worker != null ? worker.getUser().getName() : null);
    }
}
