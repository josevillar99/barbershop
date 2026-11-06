package com.peluqueria.cashclosing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peluqueria.auth.JwtService;
import com.peluqueria.booking.OpeningHourRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba marcar citas como hechas con importe/metodo de pago, y el resumen y cierre de caja
 * diario que se construye a partir de ellas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class CashClosingControllerTest {

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
                .email("trabajador-caja-" + UUID.randomUUID() + "@example.com")
                .role(Role.ADMIN).name("Trabajador de caja").phone("600000000").build());
        Worker worker = workerRepository.save(Worker.builder().user(workerUser).active(true).color("#7c3aed").build());
        workerId = worker.getId();

        List<com.peluqueria.booking.OpeningHour> template = openingHourRepository.findByWorkerIsNull();
        openingHourRepository.saveAll(template.stream()
                .map(h -> com.peluqueria.booking.OpeningHour.builder()
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

    private Long bookAppointment(LocalDate date, String startTime) throws Exception {
        Map<String, Object> body = Map.of(
                "treatmentIds", List.of(treatmentId),
                "date", date.toString(),
                "startTime", startTime,
                "clientName", "Cliente de prueba",
                "clientPhone", "611222333",
                "workerId", workerId);

        String json = mockMvc.perform(asAdmin(post("/api/admin/appointments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private void completeAppointment(Long id, Object amountCharged, String paymentMethod) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("completed", true);
        body.put("amountCharged", amountCharged);
        body.put("paymentMethod", paymentMethod);

        mockMvc.perform(asAdmin(put("/api/admin/appointments/" + id + "/completed"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void marcarCitaHechaSinImporteDa400() throws Exception {
        Long id = bookAppointment(nextMonday(), "09:00");

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("completed", true);
        body.put("amountCharged", null);
        body.put("paymentMethod", "EFECTIVO");

        mockMvc.perform(asAdmin(put("/api/admin/appointments/" + id + "/completed"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Indica el importe cobrado"));
    }

    @Test
    void marcarCitaHechaSinMetodoDePagoDa400() throws Exception {
        Long id = bookAppointment(nextMonday(), "09:00");

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("completed", true);
        body.put("amountCharged", 15.0);
        body.put("paymentMethod", null);

        mockMvc.perform(asAdmin(put("/api/admin/appointments/" + id + "/completed"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Indica el metodo de pago"));
    }

    @Test
    void marcarCitaHechaConImporteYMetodoApareceEnElResumenDeCaja() throws Exception {
        LocalDate date = nextMonday();
        Long id = bookAppointment(date, "09:00");
        completeAppointment(id, 15.0, "EFECTIVO");

        mockMvc.perform(asAdmin(get("/api/admin/cash-closing")).param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.efectivoTotal").value(15.0))
                .andExpect(jsonPath("$.tarjetaTotal").value(0))
                .andExpect(jsonPath("$.total").value(15.0))
                .andExpect(jsonPath("$.appointmentCount").value(1))
                .andExpect(jsonPath("$.appointments[0].amountCharged").value(15.0))
                .andExpect(jsonPath("$.appointments[0].paymentMethod").value("EFECTIVO"))
                .andExpect(jsonPath("$.countedCash").doesNotExist());
    }

    @Test
    void desmarcarUnaCitaLimpiaImporteYMetodoYLaQuitaDelResumen() throws Exception {
        LocalDate date = nextMonday();
        Long id = bookAppointment(date, "09:00");
        completeAppointment(id, 15.0, "EFECTIVO");

        Map<String, Object> undo = new java.util.HashMap<>();
        undo.put("completed", false);
        undo.put("amountCharged", null);
        undo.put("paymentMethod", null);
        mockMvc.perform(asAdmin(put("/api/admin/appointments/" + id + "/completed"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(undo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountCharged").doesNotExist())
                .andExpect(jsonPath("$.paymentMethod").doesNotExist());

        mockMvc.perform(asAdmin(get("/api/admin/cash-closing")).param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentCount").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void guardarCierreDeCajaCalculaElDescuadreFrenteAlEfectivoEsperado() throws Exception {
        LocalDate date = nextMonday();
        Long id = bookAppointment(date, "09:00");
        completeAppointment(id, 15.0, "EFECTIVO");

        Map<String, Object> closingBody = Map.of("countedCash", 20.0);
        mockMvc.perform(asAdmin(put("/api/admin/cash-closing")).param("date", date.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(closingBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countedCash").value(20.0))
                .andExpect(jsonPath("$.difference").value(5.0));

        // Reabrir el resumen (sin volver a cerrar) debe seguir mostrando el cierre guardado.
        mockMvc.perform(asAdmin(get("/api/admin/cash-closing")).param("date", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countedCash").value(20.0))
                .andExpect(jsonPath("$.difference").value(5.0));
    }

    @Test
    void guardarCierreDeCajaSinEfectivoContadoDa400() throws Exception {
        LocalDate date = nextMonday();

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("countedCash", null);
        mockMvc.perform(asAdmin(put("/api/admin/cash-closing")).param("date", date.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Indica el efectivo contado"));
    }
}
