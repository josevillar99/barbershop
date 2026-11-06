package com.peluqueria.user;

import com.peluqueria.auth.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba los endpoints exclusivos de superadministrador: el limite de rol (ADMIN no basta,
 * hace falta SUPERADMIN), que nadie puede eliminar su propia cuenta, y el ascenso a admin
 * (incluyendo que no se puede "ascender" a alguien que ya es superadmin).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class SuperAdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;

    private MockHttpServletRequestBuilder asRole(MockHttpServletRequestBuilder builder, String email, String role) {
        return builder.header("Authorization", "Bearer " + jwtService.generateToken(email, role));
    }

    private User crearUsuario(Role role) {
        return userRepository.save(User.builder()
                .email("usuario-" + UUID.randomUUID() + "@example.com")
                .role(role)
                .name("Persona de prueba")
                .phone("600000000")
                .build());
    }

    @Test
    void unAdminNormalNoPuedeListarUsuariosDeSuperadmin() throws Exception {
        mockMvc.perform(asRole(get("/api/superadmin/users"), "admin-" + UUID.randomUUID() + "@example.com", "ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unSuperadminNoPuedeEliminarSuPropiaCuenta() throws Exception {
        User self = crearUsuario(Role.SUPERADMIN);

        mockMvc.perform(asRole(delete("/api/superadmin/users/" + self.getId()), self.getEmail(), "SUPERADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No puedes eliminar tu propia cuenta"));

        assertEquals(true, userRepository.findById(self.getId()).isPresent());
    }

    @Test
    void ascenderAAdminFuncionaPeroNoSePuedeAscenderAQuienYaEsSuperadmin() throws Exception {
        User cliente = crearUsuario(Role.USER);
        User yaSuperadmin = crearUsuario(Role.SUPERADMIN);
        String requesterEmail = "otro-superadmin-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(asRole(post("/api/superadmin/users/" + cliente.getId() + "/make-admin"), requesterEmail, "SUPERADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        assertEquals(Role.ADMIN, userRepository.findById(cliente.getId()).orElseThrow().getRole());

        mockMvc.perform(asRole(post("/api/superadmin/users/" + yaSuperadmin.getId() + "/make-admin"), requesterEmail, "SUPERADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ese usuario ya es superadministrador"));
    }
}
