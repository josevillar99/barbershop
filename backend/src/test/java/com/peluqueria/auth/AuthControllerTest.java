package com.peluqueria.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peluqueria.audit.AuditAction;
import com.peluqueria.audit.AuditLogRepository;
import com.peluqueria.user.Role;
import com.peluqueria.user.User;
import com.peluqueria.user.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba los endpoints de autenticacion de principio a fin (HTTP -> controlador -> servicio
 * -> base de datos real), incluyendo el limitador de intentos por IP y que el log de
 * auditoria registra la IP. Cada test usa una IP simulada distinta (via X-Forwarded-For)
 * para no compartir contador con los demas, ya que el limitador vive en un bean singleton
 * que no se resetea entre tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private SignupTokenRepository signupTokenRepository;
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    // Se sustituye el mailSender real por un mock para no enviar correos de verdad en los
    // tests (ni depender de credenciales SMTP): createMimeMessage() sigue devolviendo un
    // MimeMessage utilizable (solo que nunca llega a salir a la red), y send(...) no hace nada.
    @MockBean
    private JavaMailSender mailSender;

    private String ip;

    @BeforeEach
    void setUp() {
        ip = UUID.randomUUID().toString();
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private MockHttpServletRequestBuilder postJson(String uri, Object body) throws Exception {
        return post("/api/auth" + uri)
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private User crearUsuarioConPassword(String email, String rawPassword, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .role(role)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .name("Usuario de prueba")
                .phone("600000000")
                .build());
    }

    // ---- signup ----

    @Test
    void signupConEmailNuevoCreaUnTokenYQuedaEnAceptado() throws Exception {
        String email = "nuevo-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(postJson("/signup", Map.of("email", email)))
                .andExpect(status().isAccepted());

        assertTrue(signupTokenRepository.findFirstByEmailIgnoreCaseAndCreatedAtAfterOrderByCreatedAtDesc(
                email, Instant.now().minusSeconds(10)).isPresent());
    }

    @Test
    void signupConEmailYaRegistradoDaConflicto() throws Exception {
        String email = "existente-" + UUID.randomUUID() + "@example.com";
        crearUsuarioConPassword(email, "Password123", Role.USER);

        mockMvc.perform(postJson("/signup", Map.of("email", email)))
                .andExpect(status().isConflict());
    }

    @Test
    void signupDosVecesSeguidasDaDemasiadasPeticionesPorElCooldown() throws Exception {
        String email = "cooldown-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(postJson("/signup", Map.of("email", email))).andExpect(status().isAccepted());
        mockMvc.perform(postJson("/signup", Map.of("email", email))).andExpect(status().isTooManyRequests());
    }

    @Test
    void signupConEmailMalFormadoDaBadRequest() throws Exception {
        mockMvc.perform(postJson("/signup", Map.of("email", "no-es-un-email")))
                .andExpect(status().isBadRequest());
    }

    // ---- signup/complete ----

    @Test
    void completarRegistroConTokenValidoCreaLaCuenta() throws Exception {
        String email = "completa-" + UUID.randomUUID() + "@example.com";
        String token = UUID.randomUUID().toString();
        signupTokenRepository.save(SignupToken.builder()
                .email(email).token(token).role("USER")
                .expiresAt(Instant.now().plusSeconds(600)).used(false).build());

        mockMvc.perform(postJson("/signup/complete", Map.of(
                        "token", token, "password", "Password123", "name", "Nueva Clienta", "phone", "611222333")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Nueva Clienta"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.profileComplete").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty());

        User created = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertTrue(passwordEncoder.matches("Password123", created.getPasswordHash()));
    }

    @Test
    void completarRegistroConTokenCaducadoRechaza() throws Exception {
        String email = "caducado-" + UUID.randomUUID() + "@example.com";
        String token = UUID.randomUUID().toString();
        signupTokenRepository.save(SignupToken.builder()
                .email(email).token(token).role("USER")
                .expiresAt(Instant.now().minusSeconds(1)).used(false).build());

        mockMvc.perform(postJson("/signup/complete", Map.of(
                        "token", token, "password", "Password123", "name", "X", "phone", "600000000")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void completarRegistroConTokenYaUsadoRechaza() throws Exception {
        String email = "reusado-" + UUID.randomUUID() + "@example.com";
        String token = UUID.randomUUID().toString();
        signupTokenRepository.save(SignupToken.builder()
                .email(email).token(token).role("USER")
                .expiresAt(Instant.now().plusSeconds(600)).used(true).build());

        mockMvc.perform(postJson("/signup/complete", Map.of(
                        "token", token, "password", "Password123", "name", "X", "phone", "600000000")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void completarRegistroConTokenDesconocidoRechaza() throws Exception {
        mockMvc.perform(postJson("/signup/complete", Map.of(
                        "token", "token-que-no-existe", "password", "Password123", "name", "X", "phone", "600000000")))
                .andExpect(status().isUnauthorized());
    }

    // ---- login ----

    @Test
    void loginConCredencialesCorrectasDevuelveTokenYRegistraAuditoria() throws Exception {
        String email = "login-ok-" + UUID.randomUUID() + "@example.com";
        crearUsuarioConPassword(email, "Password123", Role.USER);

        mockMvc.perform(postJson("/login", Map.of("email", email, "password", "Password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        var page = auditLogRepository.search(email, AuditAction.LOGIN, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id")));
        assertFalse(page.getContent().isEmpty());
        assertTrue(page.getContent().get(0).getDetails().contains("(IP: " + ip + ")"));
    }

    @Test
    void loginConContrasenaIncorrectaDaNoAutorizado() throws Exception {
        String email = "login-mal-" + UUID.randomUUID() + "@example.com";
        crearUsuarioConPassword(email, "Password123", Role.USER);

        mockMvc.perform(postJson("/login", Map.of("email", email, "password", "otra-cosa")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginConEmailInexistenteDaElMismoErrorGenerico() throws Exception {
        mockMvc.perform(postJson("/login", Map.of("email", "no-existe-" + UUID.randomUUID() + "@example.com", "password", "cualquiera")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email o contrasena incorrectos"));
    }

    @Test
    void loginEnUnaCuentaSoloDeGoogleSinContrasenaDaNoAutorizado() throws Exception {
        String email = "solo-google-" + UUID.randomUUID() + "@example.com";
        userRepository.save(User.builder().email(email).role(Role.USER).googleId("google-" + UUID.randomUUID()).build());

        mockMvc.perform(postJson("/login", Map.of("email", email, "password", "cualquiera")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trasDiezIntentosDeLoginLaMismaIpQuedaBloqueada() throws Exception {
        String email = "bloqueo-" + UUID.randomUUID() + "@example.com";
        crearUsuarioConPassword(email, "Password123", Role.USER);

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(postJson("/login", Map.of("email", email, "password", "mal")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(postJson("/login", Map.of("email", email, "password", "Password123")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void elBloqueoPorIpNoAfectaAOtraIp() throws Exception {
        String email = "bloqueo-aislado-" + UUID.randomUUID() + "@example.com";
        crearUsuarioConPassword(email, "Password123", Role.USER);

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(postJson("/login", Map.of("email", email, "password", "mal")))
                    .andExpect(status().isUnauthorized());
        }

        String otraIp = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", otraIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Password123"))))
                .andExpect(status().isOk());
    }

    // ---- forgot-password / reset-password ----

    @Test
    void olvideMiContrasenaConCuentaExistenteCreaUnToken() throws Exception {
        String email = "olvido-" + UUID.randomUUID() + "@example.com";
        crearUsuarioConPassword(email, "Password123", Role.USER);

        mockMvc.perform(postJson("/forgot-password", Map.of("email", email)))
                .andExpect(status().isAccepted());

        assertTrue(passwordResetTokenRepository.findFirstByEmailIgnoreCaseAndCreatedAtAfterOrderByCreatedAtDesc(
                email, Instant.now().minusSeconds(10)).isPresent());
    }

    @Test
    void olvideMiContrasenaConEmailInexistenteNoCreaTokenNiFalla() throws Exception {
        String email = "fantasma-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(postJson("/forgot-password", Map.of("email", email)))
                .andExpect(status().isAccepted());

        assertTrue(passwordResetTokenRepository.findFirstByEmailIgnoreCaseAndCreatedAtAfterOrderByCreatedAtDesc(
                email, Instant.now().minusSeconds(10)).isEmpty());
    }

    @Test
    void olvideMiContrasenaDosVecesSeguidasDaDemasiadasPeticiones() throws Exception {
        String email = "olvido-cooldown-" + UUID.randomUUID() + "@example.com";
        crearUsuarioConPassword(email, "Password123", Role.USER);

        mockMvc.perform(postJson("/forgot-password", Map.of("email", email))).andExpect(status().isAccepted());
        mockMvc.perform(postJson("/forgot-password", Map.of("email", email))).andExpect(status().isTooManyRequests());
    }

    @Test
    void restablecerContrasenaConTokenValidoCambiaLaContrasenaYMarcaElTokenUsado() throws Exception {
        String email = "reset-ok-" + UUID.randomUUID() + "@example.com";
        crearUsuarioConPassword(email, "ViejaPassword1", Role.USER);
        String token = UUID.randomUUID().toString();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .email(email).token(token).expiresAt(Instant.now().plusSeconds(600)).used(false).build());

        mockMvc.perform(postJson("/reset-password", Map.of("token", token, "newPassword", "NuevaPassword1")))
                .andExpect(status().isOk());

        User updated = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertTrue(passwordEncoder.matches("NuevaPassword1", updated.getPasswordHash()));
        assertTrue(passwordResetTokenRepository.findByToken(token).orElseThrow().isUsed());
    }

    @Test
    void restablecerContrasenaConElMismoTokenDosVecesFallaLaSegunda() throws Exception {
        String email = "reset-doble-" + UUID.randomUUID() + "@example.com";
        crearUsuarioConPassword(email, "ViejaPassword1", Role.USER);
        String token = UUID.randomUUID().toString();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .email(email).token(token).expiresAt(Instant.now().plusSeconds(600)).used(false).build());

        mockMvc.perform(postJson("/reset-password", Map.of("token", token, "newPassword", "NuevaPassword1")))
                .andExpect(status().isOk());
        mockMvc.perform(postJson("/reset-password", Map.of("token", token, "newPassword", "OtraPassword2")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void restablecerContrasenaConTokenCaducadoRechaza() throws Exception {
        String email = "reset-caducado-" + UUID.randomUUID() + "@example.com";
        crearUsuarioConPassword(email, "ViejaPassword1", Role.USER);
        String token = UUID.randomUUID().toString();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .email(email).token(token).expiresAt(Instant.now().minus(1, ChronoUnit.SECONDS)).used(false).build());

        mockMvc.perform(postJson("/reset-password", Map.of("token", token, "newPassword", "NuevaPassword1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void restablecerContrasenaConContrasenaCortaDaBadRequest() throws Exception {
        mockMvc.perform(postJson("/reset-password", Map.of("token", "cualquiera", "newPassword", "abc")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trasRestablecerLaContrasenaElTokenAntiguoDejaDeSerValidoPeroElNuevoLoginFunciona() throws Exception {
        String email = "reset-invalida-token-" + UUID.randomUUID() + "@example.com";
        crearUsuarioConPassword(email, "ViejaPassword1", Role.USER);

        String loginResponse = mockMvc.perform(postJson("/login", Map.of("email", email, "password", "ViejaPassword1")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String oldToken = objectMapper.readTree(loginResponse).get("token").asText();

        String resetToken = UUID.randomUUID().toString();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .email(email).token(resetToken).expiresAt(Instant.now().plusSeconds(600)).used(false).build());
        mockMvc.perform(postJson("/reset-password", Map.of("token", resetToken, "newPassword", "NuevaPassword1")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isForbidden());

        String newLoginResponse = mockMvc.perform(postJson("/login", Map.of("email", email, "password", "NuevaPassword1")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newToken = objectMapper.readTree(newLoginResponse).get("token").asText();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }
}
