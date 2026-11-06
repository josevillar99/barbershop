package com.peluqueria.worker;

import com.peluqueria.booking.AppointmentRepository;
import com.peluqueria.booking.OpeningHour;
import com.peluqueria.booking.OpeningHourRepository;
import com.peluqueria.booking.dto.OpeningHourRequest;
import com.peluqueria.booking.dto.OpeningHourView;
import com.peluqueria.catalog.Treatment;
import com.peluqueria.catalog.TreatmentRepository;
import com.peluqueria.common.ApiException;
import com.peluqueria.user.Role;
import com.peluqueria.user.User;
import com.peluqueria.user.UserRepository;
import com.peluqueria.worker.dto.WorkerView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/** Logica de negocio de los perfiles de trabajador: alta, horario semanal, color y baja. */
@Service
@RequiredArgsConstructor
public class WorkerService {

    /**
     * Paleta fija de colores asignables a un trabajador (evita rojo/verde, ya
     * usados en la app para cancelar/disponible). Debe coincidir exactamente
     * con la paleta del frontend (frontend/src/workerColors.ts).
     */
    static final List<String> COLOR_PALETTE = List.of(
            "#2563eb", "#7c3aed", "#d97706", "#0891b2", "#db2777",
            "#4f46e5", "#0e7490", "#9333ea", "#be185d", "#57534e"
    );

    private final WorkerRepository workerRepository;
    private final OpeningHourRepository openingHourRepository;
    private final AppointmentRepository appointmentRepository;
    private final TreatmentRepository treatmentRepository;
    private final UserRepository userRepository;

    /**
     * Crea el perfil de trabajador para una cuenta recien creada por
     * invitacion, copiando como horario inicial la plantilla heredada del
     * antiguo horario unico del salon (sin tratamientos asignados todavia) y
     * asignandole el primer color de la paleta que ningun otro trabajador
     * activo este usando ya.
     */
    @Transactional
    public Worker createFromInvite(User user) {
        Worker worker = workerRepository.save(Worker.builder().user(user).active(true).color(nextAvailableColor()).build());
        List<OpeningHour> template = openingHourRepository.findByWorkerIsNull();
        List<OpeningHour> copies = template.stream()
                .map(h -> OpeningHour.builder()
                        .dayOfWeek(h.getDayOfWeek())
                        .startTime(h.getStartTime())
                        .endTime(h.getEndTime())
                        .worker(worker)
                        .build())
                .toList();
        openingHourRepository.saveAll(copies);
        return worker;
    }

    /**
     * Convierte a una cuenta ya existente (que todavia no es trabajador) en
     * trabajador: le asigna el rol ADMIN si no lo tenia ya, y le crea el
     * perfil de trabajador (mismo horario plantilla y logica de color que al
     * invitar a alguien nuevo).
     */
    @Transactional
    public WorkerView promoteExistingUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (workerRepository.findByUserId(userId).isPresent()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ese usuario ya es trabajador");
        }
        if (user.getRole() == Role.USER) {
            user.setRole(Role.ADMIN);
        }
        return toView(createFromInvite(user));
    }

    /**
     * Quita el perfil de trabajador a alguien (deja de ser reservable, se
     * desvinculan sus citas pasadas) sin borrar su cuenta ni tocar su rol.
     * Devuelve su nombre, para poder registrarlo en el log de auditoria.
     */
    @Transactional
    public String removeWorkerProfile(Long workerId) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Trabajador no encontrado"));
        String name = worker.getUser().getName();
        appointmentRepository.detachWorker(workerId);
        openingHourRepository.deleteByWorkerId(workerId);
        workerRepository.delete(worker);
        return name;
    }

    /** Lista todos los trabajadores (activos e inactivos), con su horario y los servicios que saben hacer. */
    @Transactional(readOnly = true)
    public List<WorkerView> listAll() {
        return workerRepository.findAll().stream().map(this::toView).toList();
    }

    /** Sustituye el horario semanal de un trabajador por el enviado desde el panel. */
    @Transactional
    public List<OpeningHourView> replaceHours(Long workerId, List<OpeningHourRequest> requests) {
        Worker worker = getActiveWorker(workerId);
        List<OpeningHour> entries = requests.stream().map(r -> toEntity(r, worker)).toList();
        openingHourRepository.deleteByWorkerId(workerId);
        openingHourRepository.saveAll(entries);
        return hoursView(workerId);
    }

    /** Cambia el color asignado a un trabajador; debe ser uno de los de la paleta fija. */
    @Transactional
    public WorkerView updateColor(Long workerId, String color) {
        Worker worker = getActiveWorker(workerId);
        if (!COLOR_PALETTE.contains(color)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ese color no esta disponible");
        }
        worker.setColor(color);
        return toView(worker);
    }

    /** Elige el color de la paleta menos usado entre los trabajadores activos actuales. */
    private String nextAvailableColor() {
        Set<String> used = workerRepository.findByActiveTrueOrderByUserNameAsc().stream()
                .map(Worker::getColor)
                .collect(java.util.stream.Collectors.toSet());
        return COLOR_PALETTE.stream()
                .filter(c -> !used.contains(c))
                .findFirst()
                .orElseGet(() -> COLOR_PALETTE.get(used.size() % COLOR_PALETTE.size()));
    }

    /** Busca un trabajador activo por su id o lanza un error si no existe o esta desactivado. */
    @Transactional(readOnly = true)
    public Worker getActiveWorker(Long id) {
        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Trabajador no encontrado"));
        if (!worker.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ese trabajador ya no esta activo");
        }
        return worker;
    }

    /**
     * Desvincula (sin borrar) las citas del trabajador ligado a ese usuario y
     * elimina su perfil y horario, antes de que {@code UserService} borre la
     * cuenta. No hace nada si ese usuario no es (o no era) trabajador.
     */
    @Transactional
    public void deleteForUser(Long userId) {
        workerRepository.findByUserId(userId).ifPresent(worker -> {
            appointmentRepository.detachWorker(worker.getId());
            openingHourRepository.deleteByWorkerId(worker.getId());
            workerRepository.delete(worker);
        });
    }

    private OpeningHour toEntity(OpeningHourRequest request, Worker worker) {
        if (request.dayOfWeek() < 1 || request.dayOfWeek() > 7) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Dia de la semana invalido: " + request.dayOfWeek());
        }
        LocalTime start = parseTime(request.startTime());
        LocalTime end = parseTime(request.endTime());
        if (!start.isBefore(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La hora de inicio debe ser anterior a la de fin");
        }
        return OpeningHour.builder().dayOfWeek(request.dayOfWeek()).startTime(start).endTime(end).worker(worker).build();
    }

    private LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Hora invalida: " + text);
        }
    }

    private List<OpeningHourView> hoursView(Long workerId) {
        return openingHourRepository.findByWorkerIdOrderByDayOfWeekAscStartTimeAsc(workerId).stream()
                .map(h -> new OpeningHourView(h.getDayOfWeek(), format(h.getStartTime()), format(h.getEndTime())))
                .toList();
    }

    private WorkerView toView(Worker worker) {
        List<Long> treatmentIds = treatmentRepository.findByWorkersId(worker.getId()).stream()
                .map(Treatment::getId)
                .toList();
        return new WorkerView(
                worker.getId(),
                worker.getUser().getId(),
                worker.getUser().getName(),
                worker.getUser().getEmail(),
                worker.isActive(),
                worker.getColor(),
                treatmentIds,
                hoursView(worker.getId()));
    }

    private static String format(LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }
}
