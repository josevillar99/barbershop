package com.peluqueria.auth;

import com.peluqueria.auth.dto.AuthResponse;
import com.peluqueria.common.ApiException;
import com.peluqueria.config.AppProperties;
import com.peluqueria.user.Role;
import com.peluqueria.user.RoleAssigner;
import com.peluqueria.user.User;
import com.peluqueria.user.UserRepository;
import com.peluqueria.worker.WorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.mail.internet.MimeMessage;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * Logica de negocio de autenticacion: alta de cuentas por email, login y generacion de tokens de sesion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SignupTokenRepository signupTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final JwtService jwtService;
    private final AppProperties appProperties;
    private final RoleAssigner roleAssigner;
    private final WorkerService workerService;

    /** Genera y envia por email un enlace de registro, comprobando que el email no tenga ya cuenta ni una peticion reciente. */
    @Transactional
    public void signup(String rawEmail) {
        createSignupToken(rawEmail, Role.USER);
    }

    /**
     * Genera y envia por email un enlace de registro que, al completarse,
     * crea la cuenta directamente como ADMIN con perfil de trabajador (ver
     * {@link #completeSignup}), en vez del alta normal como cliente.
     */
    @Transactional
    public void inviteWorker(String rawEmail) {
        createSignupToken(rawEmail, Role.ADMIN);
    }

    /** Logica comun de alta de token de registro, compartida por el alta normal y la invitacion de trabajador. */
    private void createSignupToken(String rawEmail, Role role) {
        String email = normalize(rawEmail);

        userRepository.findByEmailIgnoreCase(email).ifPresent(existing -> {
            if (existing.getPasswordHash() != null) {
                throw new ApiException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese email");
            }
        });

        Instant cooldownStart = Instant.now().minusSeconds(appProperties.auth().signupResendCooldownSeconds());
        signupTokenRepository.findFirstByEmailIgnoreCaseAndCreatedAtAfterOrderByCreatedAtDesc(email, cooldownStart)
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                            "Espera unos segundos antes de pedir otro enlace");
                });

        String token = generateToken();
        SignupToken signupToken = SignupToken.builder()
                .email(email)
                .token(token)
                .role(role.name())
                .expiresAt(Instant.now().plus(appProperties.auth().signupTokenTtlMinutes(), ChronoUnit.MINUTES))
                .used(false)
                .build();
        signupTokenRepository.save(signupToken);

        sendSignupEmail(email, token, role);
    }

    /** Valida el token de registro y crea (o completa) la cuenta del usuario con su contrasena y datos. */
    @Transactional
    public AuthResponse completeSignup(String rawToken, String password, String name, String phone) {
        SignupToken signupToken = signupTokenRepository.findByToken(rawToken)
                .filter(t -> !t.isUsed())
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "El enlace no es valido o ha caducado"));

        String email = normalize(signupToken.getEmail());
        User user = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> User.builder().email(email).build());
        if (user.getPasswordHash() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese email");
        }

        user.setPasswordHash(passwordEncoder.encode(password));
        user.setName(name);
        user.setPhone(phone);
        Role tokenRole = Role.valueOf(signupToken.getRole());
        if (user.getRole() == null) {
            user.setRole(tokenRole == Role.ADMIN ? Role.ADMIN : roleAssigner.resolveInitialRole(email));
        }
        userRepository.save(user);

        if (tokenRole == Role.ADMIN) {
            workerService.createFromInvite(user);
        }

        signupToken.setUsed(true);
        signupTokenRepository.save(signupToken);

        return toAuthResponse(user);
    }

    /** Comprueba email y contrasena y devuelve la respuesta de autenticacion si son correctos. */
    @Transactional
    public AuthResponse login(String rawEmail, String password) {
        String email = normalize(rawEmail);
        User user = userRepository.findByEmailIgnoreCase(email)
                .filter(u -> u.getPasswordHash() != null)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email o contrasena incorrectos"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email o contrasena incorrectos");
        }

        promoteToSuperadminIfConfigured(user, email);
        return toAuthResponse(user);
    }

    /**
     * Asciende a SUPERADMIN una cuenta ya existente si su email coincide con
     * el configurado, incluso si se creo antes de fijar esa configuracion.
     */
    private void promoteToSuperadminIfConfigured(User user, String email) {
        if (user.getRole() != Role.SUPERADMIN && roleAssigner.isSuperadminEmail(email)) {
            user.setRole(Role.SUPERADMIN);
        }
    }

    /**
     * Genera y envia por email un enlace para restablecer la contrasena, si existe una
     * cuenta con ese email y contrasena (no revela si el email existe o no: si no hay
     * cuenta, simplemente no envia nada y no da ningun error).
     */
    @Transactional
    public void forgotPassword(String rawEmail) {
        String email = normalize(rawEmail);
        User user = userRepository.findByEmailIgnoreCase(email)
                .filter(u -> u.getPasswordHash() != null)
                .orElse(null);
        if (user == null) {
            return;
        }

        Instant cooldownStart = Instant.now().minusSeconds(appProperties.auth().signupResendCooldownSeconds());
        passwordResetTokenRepository.findFirstByEmailIgnoreCaseAndCreatedAtAfterOrderByCreatedAtDesc(email, cooldownStart)
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                            "Espera unos segundos antes de pedir otro enlace");
                });

        String token = generateToken();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email(email)
                .token(token)
                .expiresAt(Instant.now().plus(appProperties.auth().signupTokenTtlMinutes(), ChronoUnit.MINUTES))
                .used(false)
                .build();
        passwordResetTokenRepository.save(resetToken);

        sendPasswordResetEmail(email, token);
    }

    /** Valida el token de restablecimiento y fija la contrasena nueva del usuario; devuelve su email para el log de auditoria. */
    @Transactional
    public String resetPassword(String rawToken, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(rawToken)
                .filter(t -> !t.isUsed())
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "El enlace no es valido o ha caducado"));

        User user = userRepository.findByEmailIgnoreCase(resetToken.getEmail())
                .filter(u -> u.getPasswordHash() != null)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "El enlace no es valido o ha caducado"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setCredentialsChangedAt(Instant.now());
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        return user.getEmail();
    }

    /** Construye la respuesta de autenticacion (token JWT y datos basicos) para un usuario ya validado. */
    AuthResponse toAuthResponse(User user) {
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name(), user.getCredentialsChangedAt());
        boolean profileComplete = user.getName() != null && user.getPhone() != null;
        return new AuthResponse(token, user.getEmail(), user.getName(), user.getRole().name(), profileComplete,
                user.getTermsAcceptedAt() != null);
    }

    /**
     * Envia el correo con el enlace para completar el registro (con texto
     * distinto si es una invitacion de trabajador), como un boton en HTML en
     * vez de mostrar la URL con el token en claro.
     */
    private void sendSignupEmail(String email, String token, Role role) {
        String link = appProperties.frontend().baseUrl() + "/completar-registro?token=" + token;
        boolean worker = role == Role.ADMIN;
        String subject = worker ? "Te han invitado como trabajador - Castor Barber Shop" : "Completa tu registro - Castor Barber Shop";
        String intro = worker
                ? "Te han invitado a crear tu cuenta de trabajador en Castor Barber Shop. Pulsa el boton para continuar."
                : "Para terminar de crear tu cuenta en Castor Barber Shop, pulsa el siguiente boton.";
        String buttonLabel = worker ? "Crear cuenta de trabajador" : "Crear mi cuenta";
        String html = signupEmailHtml(intro, link, buttonLabel, appProperties.auth().signupTokenTtlMinutes());

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setFrom(appProperties.mail().from());
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mimeMessage);
        } catch (Exception ex) {
            log.error("No se pudo enviar el email de registro a {}", email, ex);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo enviar el email, intentalo mas tarde");
        }
    }

    /**
     * Envia el correo con el enlace para restablecer la contrasena (misma
     * plantilla que el email de registro, con su propio texto y botón).
     */
    private void sendPasswordResetEmail(String email, String token) {
        String link = appProperties.frontend().baseUrl() + "/restablecer-contrasena?token=" + token;
        String subject = "Restablece tu contrasena - Castor Barber Shop";
        String intro = "Hemos recibido una solicitud para restablecer tu contrasena. Pulsa el boton para elegir una nueva.";
        String html = signupEmailHtml(intro, link, "Restablecer contrasena", appProperties.auth().signupTokenTtlMinutes());

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setFrom(appProperties.mail().from());
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mimeMessage);
        } catch (Exception ex) {
            log.error("No se pudo enviar el email de restablecimiento de contrasena a {}", email, ex);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo enviar el email, intentalo mas tarde");
        }
    }

    /** Plantilla HTML minima del correo de registro: un boton de accion en vez de un enlace en texto plano. */
    private String signupEmailHtml(String intro, String link, String buttonLabel, int ttlMinutes) {
        return "<!doctype html><html><body style=\"font-family:Arial,Helvetica,sans-serif;background:#f6f5f3;padding:32px;margin:0;\">"
                + "<div style=\"max-width:480px;margin:0 auto;background:#ffffff;border-radius:10px;padding:32px;\">"
                + "<h1 style=\"color:#8f4f16;font-size:1.3rem;margin:0 0 16px;\">Castor Barber Shop</h1>"
                + "<p style=\"color:#2b2622;font-size:0.95rem;\">" + intro + "</p>"
                + "<p style=\"text-align:center;margin:32px 0;\">"
                + "<a href=\"" + link + "\" style=\"background:#b5651d;color:#ffffff;text-decoration:none;"
                + "padding:12px 28px;border-radius:6px;font-weight:bold;display:inline-block;font-size:0.95rem;\">"
                + buttonLabel + "</a></p>"
                + "<p style=\"color:#7a736c;font-size:0.8rem;\">Este enlace caduca en " + ttlMinutes + " minutos. "
                + "Si no has sido tu, puedes ignorar este correo.</p>"
                + "</div></body></html>";
    }

    /** Genera un token aleatorio de alta entropia para los enlaces de registro. */
    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Normaliza el email quitando espacios y pasandolo a minusculas para comparaciones consistentes. */
    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
