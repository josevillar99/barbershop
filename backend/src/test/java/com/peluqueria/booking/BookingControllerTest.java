package com.peluqueria.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peluqueria.auth.JwtService;
import com.peluqueria.catalog.Treatment;
import com.peluqueria.catalog.TreatmentRepository;
import com.peluqueria.user.Role;
import com.peluqueria.user.User;
import com.peluqueria.user.UserRepository;
import com.peluqueria.worker.Worker;
import com.peluqueria.worker.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba la capa HTTP de citas y bloqueos administrativos: el doble-reserva del mismo hueco,
 * cancelar una cita ya cancelada, un bloqueo que se solapa con una cita existente, y borrar un
 * bloqueo que no existe. El algoritmo de disponibilidad en si ya esta cubierto a fondo por
 * BookingEngineIntegrationTest; aqui solo se prueba el comportamiento a traves del controlador.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WorkerRepository workerRepository;
    @Autowired
    private TreatmentRepository treatmentRepository;
    @Autowired
    private OpeningHourRepository openingHourRepository;

    private Long workerId;
    private Long treatmentId;
    private String adminEmail;

    @BeforeEach
    void setUp() {
        adminEmail = "admin-" + UUID.randomUUID() + "@example.com";
        User workerUser = userRepository.save(User.builder()
                .email("trabajador-booking-" + UUID.randomUUID() + "@example.com")
                .role(Role.ADMIN).name("Trabajador de citas").phone("600000000").build());
        Worker worker = workerRepository.save(Worker.builder().user(workerUser).active(true).color("#7c3aed").build());
        workerId = worker.getId();

        List<OpeningHour> template = openingHourRepository.findByWorkerIsNull();
        openingHourRepository.saveAll(template.stream()
                .map(h -> OpeningHour.builder()
                        .dayOfWeek(h.getDayOfWeek()).startTime(h.getStartTime()).endTime(h.getEndTime())
                        .worker(worker).build())
                .toList());

        Treatment corte = treatmentRepository.findAll().stream()
                .filter(t -> t.getName().equals("Corte")).findFirst().orElseThrow();
        corte.getWorkers().add(worker);
        treatmentRepository.save(corte);
        treatmentId = corte.getId();
    }

    private LocalDate nextMonday() {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + jwtService.generateToken(adminEmail, "ADMIN"));
    }

    private String bookAppointment(LocalDate date, String startTime) throws Exception {
        Map<String, Object> body = Map.of(
                "treatmentIds", List.of(treatmentId),
                "date", date.toString(),
                "startTime", startTime,
                "clientName", "Cliente de prueba",
                "clientPhone", "611222333",
                "workerId", workerId);

        return mockMvc.perform(asAdmin(post("/api/admin/appointments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void reservarElMismoHuecoDelMismoTrabajadorDosVecesDaConflicto() throws Exception {
        LocalDate date = nextMonday();
        bookAppointment(date, "09:00");

        Map<String, Object> body = Map.of(
                "treatmentIds", List.of(treatmentId),
                "date", date.toString(),
                "startTime", "09:00",
                "clientName", "Otro cliente",
                "clientPhone", "699888777",
                "workerId", workerId);

        mockMvc.perform(asAdmin(post("/api/admin/appointments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Esa hora ya no esta disponible"));
    }

    @Test
    void cancelarUnaCitaYaCanceladaDaConflicto() throws Exception {
        String json = bookAppointment(nextMonday(), "09:00");
        Long appointmentId = objectMapper.readTree(json).get("id").asLong();

        mockMvc.perform(asAdmin(post("/api/admin/appointments/" + appointmentId + "/cancel")))
                .andExpect(status().isOk());

        mockMvc.perform(asAdmin(post("/api/admin/appointments/" + appointmentId + "/cancel")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Esa cita no tiene una reserva activa"));
    }

    @Test
    void unBloqueoQueSeSolapaConUnaCitaExistenteDaConflicto() throws Exception {
        LocalDate date = nextMonday();
        bookAppointment(date, "09:00");

        Map<String, Object> blockBody = Map.of(
                "date", date.toString(),
                "startTime", "09:00",
                "endTime", "09:30",
                "workerId", workerId);

        mockMvc.perform(asAdmin(post("/api/admin/blocks"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blockBody)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ese rango se solapa con una reserva o bloqueo existente"));
    }

    @Test
    void eliminarUnBloqueoQueNoExisteDa404() throws Exception {
        mockMvc.perform(asAdmin(delete("/api/admin/blocks/999999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Bloqueo no encontrado"));
    }
}
