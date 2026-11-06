package com.peluqueria.booking;

import com.peluqueria.booking.dto.AppointmentView;
import com.peluqueria.booking.dto.AvailabilityDayView;
import com.peluqueria.catalog.Treatment;
import com.peluqueria.catalog.TreatmentRepository;
import com.peluqueria.common.ApiException;
import com.peluqueria.user.Role;
import com.peluqueria.user.User;
import com.peluqueria.user.UserRepository;
import com.peluqueria.worker.Worker;
import com.peluqueria.worker.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica el motor de disponibilidad y reserva contra la base de datos
 * PostgreSQL real (migraciones + datos semilla), en una transaccion que se
 * revierte al final para no dejar datos de prueba. Crea su propio trabajador
 * de prueba (cualificado para Corte/Peinado/Mechas, con el horario plantilla)
 * para no depender de que ya exista un trabajador en la base de datos.
 */
@SpringBootTest
@Transactional
@Rollback
class BookingEngineIntegrationTest {

    @Autowired
    private AvailabilityService availabilityService;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private TreatmentRepository treatmentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WorkerRepository workerRepository;
    @Autowired
    private OpeningHourRepository openingHourRepository;

    private static final String TEST_EMAIL = "test-integration@example.com";
    private static final String WORKER_EMAIL = "test-worker-integration@example.com";

    private Long workerId;

    @BeforeEach
    void setUp() {
        if (userRepository.findByEmailIgnoreCase(TEST_EMAIL).isEmpty()) {
            userRepository.save(User.builder().email(TEST_EMAIL).role(Role.USER)
                    .name("Cliente de prueba").phone("600000000").build());
        }

        User workerUser = userRepository.findByEmailIgnoreCase(WORKER_EMAIL)
                .orElseGet(() -> userRepository.save(User.builder().email(WORKER_EMAIL).role(Role.ADMIN)
                        .name("Trabajador de prueba").phone("600000001").build()));
        Worker worker = workerRepository.save(Worker.builder().user(workerUser).active(true).color("#2563eb").build());
        List<OpeningHour> template = openingHourRepository.findByWorkerIsNull();
        openingHourRepository.saveAll(template.stream()
                .map(h -> OpeningHour.builder()
                        .dayOfWeek(h.getDayOfWeek())
                        .startTime(h.getStartTime())
                        .endTime(h.getEndTime())
                        .worker(worker)
                        .build())
                .toList());
        workerId = worker.getId();

        List<Treatment> treatments = treatmentRepository.findAll().stream()
                .filter(t -> List.of("Corte", "Peinado", "Mechas").contains(t.getName()))
                .toList();
        for (Treatment t : treatments) {
            t.getWorkers().add(worker);
        }
        treatmentRepository.saveAll(treatments);
    }

    private LocalDate nextMonday() {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    @Test
    void elHuecoDeEsperaDeMechasQuedaLibreParaOtroCliente() {
        Treatment mechas = treatmentRepository.findAll().stream()
                .filter(t -> t.getName().equals("Mechas")).findFirst().orElseThrow();
        // Peinado (30 min) cabe limpio en el hueco de espera de 60 min sin dejar
        // un resto menor que el minimo activo (20 min, Barba); un Corte de 45 min
        // no cabria (dejaria un resto de 15 min < 20), asi que no sirve para este caso.
        Treatment peinado = treatmentRepository.findAll().stream()
                .filter(t -> t.getName().equals("Peinado")).findFirst().orElseThrow();

        LocalDate date = nextMonday();

        List<AvailabilityDayView> mechasAvailability = availabilityService.availability(List.of(mechas.getId()), date, date);
        assertEquals(1, mechasAvailability.size());
        List<String> mechasTimes = mechasAvailability.get(0).times();
        assertTrue(mechasTimes.contains("09:00"), "Deberia poder empezar Mechas a las 09:00: " + mechasTimes);

        AppointmentView booked = bookingService.book(List.of(mechas.getId()), date, "09:00", null, workerId, TEST_EMAIL);
        assertEquals("09:00", booked.startTime());

        // Mechas 09:00 = WORK 09:00-09:30, WAIT 09:30-10:30, WORK 10:30-11:00.
        // Un Peinado deberia poder reservarse justo al empezar el hueco de espera.
        List<AvailabilityDayView> peinadoAvailability = availabilityService.availability(List.of(peinado.getId()), date, date);
        List<String> peinadoTimes = peinadoAvailability.get(0).times();
        assertTrue(peinadoTimes.contains("09:30"),
                "El hueco de espera de Mechas deberia quedar libre para un Peinado: " + peinadoTimes);
    }

    @Test
    void noSePuedeReservarDejandoUnHuecoMedianoInaprovechable() {
        Treatment corte = treatmentRepository.findAll().stream()
                .filter(t -> t.getName().equals("Corte")).findFirst().orElseThrow();
        LocalDate date = nextMonday();

        bookingService.book(List.of(corte.getId()), date, "09:00", null, workerId, TEST_EMAIL);

        // El minimo activo hoy es 20 min (Barba) y la tolerancia son 15 min.
        // Un intento a las 09:45 deja un hueco de 0 min: valido (contiguo).
        List<AvailabilityDayView> availability = availabilityService.availability(List.of(corte.getId()), date, date);
        List<String> times = availability.get(0).times();
        assertTrue(times.contains("09:45"), "Deberia permitir un inicio contiguo: " + times);

        // Un intento a las 09:55 deja un hueco de 10 min (<= 15 de tolerancia): valido,
        // se acepta como resto pequeno inevitable aunque este por debajo del minimo.
        AppointmentView tolerated = bookingService.book(List.of(corte.getId()), date, "09:55", null, workerId, TEST_EMAIL);
        assertEquals("09:55", tolerated.startTime());
        bookingService.cancelOwn(tolerated.id(), TEST_EMAIL);

        // Un intento a las 10:03 dejaria un hueco de 18 min (ni tolerable ni suficiente): invalido.
        ApiException ex = assertThrows(ApiException.class,
                () -> bookingService.book(List.of(corte.getId()), date, "10:03", null, workerId, TEST_EMAIL));
        assertEquals(409, ex.getStatus().value());
    }
}
