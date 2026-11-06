package com.peluqueria.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peluqueria.auth.JwtService;
import com.peluqueria.user.Role;
import com.peluqueria.user.User;
import com.peluqueria.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba el controlador de trabajadores: el limite de rol (solo ADMIN/SUPERADMIN), la paleta
 * fija de colores y la validacion de horario (inicio antes que fin).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class WorkerControllerTest {

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

    private MockHttpServletRequestBuilder asRole(MockHttpServletRequestBuilder builder, String email, String role) {
        return builder.header("Authorization", "Bearer " + jwtService.generateToken(email, role));
    }

    private Worker crearTrabajador() {
        User user = userRepository.save(User.builder()
                .email("trabajador-" + UUID.randomUUID() + "@example.com")
                .role(Role.ADMIN)
                .name("Trabajador de prueba")
                .phone("600000000")
                .build());
        return workerRepository.save(Worker.builder().user(user).active(true).color("#2563eb").build());
    }

    @Test
    void unUsuarioSinRolDeAdminNoPuedeListarTrabajadores() throws Exception {
        mockMvc.perform(asRole(get("/api/admin/workers"), "cliente-" + UUID.randomUUID() + "@example.com", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unColorFueraDeLaPaletaSeRechazaYUnoValidoSeGuarda() throws Exception {
        Worker worker = crearTrabajador();
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(asRole(put("/api/admin/workers/" + worker.getId() + "/color"), adminEmail, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("color", "#000000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ese color no esta disponible"));

        mockMvc.perform(asRole(put("/api/admin/workers/" + worker.getId() + "/color"), adminEmail, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("color", "#db2777"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("#db2777"));

        assertEquals("#db2777", workerRepository.findById(worker.getId()).orElseThrow().getColor());
    }

    @Test
    void unHorarioConLaHoraDeInicioIgualOPosteriorALaDeFinSeRechaza() throws Exception {
        Worker worker = crearTrabajador();
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.com";

        List<Map<String, Object>> hours = List.of(Map.of("dayOfWeek", 1, "startTime", "14:00", "endTime", "09:00"));

        mockMvc.perform(asRole(put("/api/admin/workers/" + worker.getId() + "/hours"), adminEmail, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(hours)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La hora de inicio debe ser anterior a la de fin"));
    }
}
