package com.peluqueria.catalog;

import com.peluqueria.catalog.dto.SegmentView;
import com.peluqueria.catalog.dto.TreatmentRequest;
import com.peluqueria.catalog.dto.TreatmentView;
import com.peluqueria.common.ApiException;
import com.peluqueria.worker.Worker;
import com.peluqueria.worker.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Logica de negocio para gestionar el catalogo de servicios del salon (crear, editar, listar y validar). */
@Service
@RequiredArgsConstructor
public class TreatmentService {

    private static final int DEFAULT_MIN_GAP_MINUTES = 15;

    private final TreatmentRepository treatmentRepository;
    private final WorkerRepository workerRepository;

    /** Devuelve los servicios activos, listos para mostrar a los clientes. */
    @Transactional(readOnly = true)
    public List<TreatmentView> listActive() {
        return treatmentRepository.findByActiveTrue().stream().map(this::toView).toList();
    }

    /** Devuelve todos los servicios, activos e inactivos, para la administracion. */
    @Transactional(readOnly = true)
    public List<TreatmentView> listAll() {
        return treatmentRepository.findAll().stream().map(this::toView).toList();
    }

    /** Valida y crea un nuevo servicio a partir de los datos recibidos. */
    @Transactional
    public TreatmentView create(TreatmentRequest request) {
        validate(request.segments());
        Treatment treatment = Treatment.builder()
                .name(request.name())
                .active(request.active())
                .price(request.price())
                .segments(toSegments(request.segments()))
                .build();
        return toView(treatmentRepository.save(treatment));
    }

    /** Valida y actualiza los datos y tramos de un servicio existente. */
    @Transactional
    public TreatmentView update(Long id, TreatmentRequest request) {
        validate(request.segments());
        Treatment treatment = getTreatment(id);
        treatment.setName(request.name());
        treatment.setActive(request.active());
        treatment.setPrice(request.price());
        treatment.getSegments().clear();
        treatment.getSegments().addAll(toSegments(request.segments()));
        return toView(treatment);
    }

    /**
     * El hueco minimo permitido no es un numero fijo: se recalcula como la
     * duracion total mas corta entre los servicios activos, para que la
     * regla anti-huecos-muertos se ajuste sola si cambian los servicios.
     */
    @Transactional(readOnly = true)
    public int minActiveDurationMinutes() {
        return treatmentRepository.findByActiveTrue().stream()
                .mapToInt(Treatment::totalMinutes)
                .min()
                .orElse(DEFAULT_MIN_GAP_MINUTES);
    }

    /** Busca un servicio por su id o lanza un error si no existe. */
    @Transactional(readOnly = true)
    public Treatment getTreatment(Long id) {
        return treatmentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Servicio no encontrado"));
    }

    /** Sustituye la lista de trabajadores cualificados para realizar un servicio. */
    @Transactional
    public TreatmentView replaceWorkers(Long id, List<Long> workerIds) {
        Treatment treatment = getTreatment(id);
        Set<Worker> workers = new HashSet<>(workerRepository.findAllById(workerIds));
        treatment.getWorkers().clear();
        treatment.getWorkers().addAll(workers);
        return toView(treatment);
    }

    /** Comprueba que los tramos del servicio sean validos: al menos uno, duraciones positivas y empezando/terminando en trabajo. */
    private void validate(List<SegmentView> segments) {
        if (segments.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El servicio debe tener al menos un tramo");
        }
        if (segments.stream().anyMatch(s -> s.minutes() <= 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La duracion de cada tramo debe ser mayor que 0");
        }
        if (segments.get(0).type() != SegmentType.WORK
                || segments.get(segments.size() - 1).type() != SegmentType.WORK) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El primer y el ultimo tramo deben ser de trabajo, no de espera");
        }
    }

    /** Convierte los tramos recibidos en la peticion a entidades Segment para guardarlos. */
    private List<Segment> toSegments(List<SegmentView> segments) {
        return segments.stream().map(s -> new Segment(s.type(), s.minutes())).toList();
    }

    /** Convierte la entidad Treatment en el DTO que se expone al frontend. */
    private TreatmentView toView(Treatment treatment) {
        List<SegmentView> segments = treatment.getSegments().stream()
                .map(s -> new SegmentView(s.getType(), s.getMinutes()))
                .toList();
        List<Long> workerIds = treatment.getWorkers().stream().map(Worker::getId).toList();
        return new TreatmentView(treatment.getId(), treatment.getName(), treatment.isActive(), treatment.getPrice(),
                segments, treatment.totalMinutes(), workerIds);
    }
}
