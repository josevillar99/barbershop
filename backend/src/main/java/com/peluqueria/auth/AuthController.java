package com.peluqueria.auth;

import com.peluqueria.audit.AuditAction;
import com.peluqueria.audit.AuditLogService;
import com.peluqueria.common.ClientIp;
import com.peluqueria.auth.dto.AuthResponse;
import com.peluqueria.auth.dto.CompleteSignupRequest;
import com.peluqueria.auth.dto.ForgotPasswordRequest;
import com.peluqueria.auth.dto.GoogleLoginRequest;
import com.peluqueria.auth.dto.LoginRequest;
import com.peluqueria.auth.dto.ResetPasswordRequest;
import com.peluqueria.auth.dto.SignupRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints REST de autenticacion: registro, login con email/password y login con Google.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    // Limites de intentos por IP para dificultar la fuerza bruta y el spam de emails
    // (ver AuthRateLimiter). Los flujos por token (signup/complete, reset-password) usan
    // el mismo limite generoso: el propio token de alta entropia ya hace inviable
    // adivinarlo, esto es solo una segunda barrera barata.
    private static final int LOGIN_MAX_ATTEMPTS = 10;
    private static final long LOGIN_WINDOW_SECONDS = 5 * 60;
    private static final int EMAIL_ACTION_MAX_ATTEMPTS = 10;
    private static final long EMAIL_ACTION_WINDOW_SECONDS = 60 * 60;

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;
    private final AuditLogService auditLogService;
    private final AuthRateLimiter rateLimiter;

    /** Inicia el registro enviando un email con un enlace para completar la cuenta. */
    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        rateLimiter.check("signup", ClientIp.resolve(httpRequest), EMAIL_ACTION_MAX_ATTEMPTS, EMAIL_ACTION_WINDOW_SECONDS);
        authService.signup(request.email());
        auditLogService.log(request.email(), AuditAction.SIGNUP, "Solicito registro");
        return ResponseEntity.accepted().build();
    }

    /** Completa el registro con el token recibido por email, fijando la contrasena y los datos del usuario. */
    @PostMapping("/signup/complete")
    public AuthResponse completeSignup(@Valid @RequestBody CompleteSignupRequest request, HttpServletRequest httpRequest) {
        rateLimiter.check("signup-complete", ClientIp.resolve(httpRequest), EMAIL_ACTION_MAX_ATTEMPTS, EMAIL_ACTION_WINDOW_SECONDS);
        AuthResponse response = authService.completeSignup(request.token(), request.password(), request.name(), request.phone());
        auditLogService.log(response.email(), AuditAction.SIGNUP_COMPLETE, "Completo el registro");
        return response;
    }

    /** Inicia sesion con email y contrasena. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        rateLimiter.check("login", ClientIp.resolve(httpRequest), LOGIN_MAX_ATTEMPTS, LOGIN_WINDOW_SECONDS);
        AuthResponse response = authService.login(request.email(), request.password());
        auditLogService.log(response.email(), AuditAction.LOGIN, "Inicio sesion");
        return response;
    }

    /** Inicia sesion (o registra) usando un idToken de Google. */
    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse response = googleAuthService.login(request.idToken());
        auditLogService.log(response.email(), AuditAction.LOGIN_GOOGLE, "Inicio sesion con Google");
        return response;
    }

    /** Envia (si existe la cuenta) un email con un enlace para restablecer la contrasena. */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        rateLimiter.check("forgot-password", ClientIp.resolve(httpRequest), EMAIL_ACTION_MAX_ATTEMPTS, EMAIL_ACTION_WINDOW_SECONDS);
        authService.forgotPassword(request.email());
        auditLogService.log(request.email(), AuditAction.PASSWORD_RESET_REQUEST, "Solicito restablecer su contrasena");
        return ResponseEntity.accepted().build();
    }

    /** Fija una contrasena nueva a partir del token recibido por email. */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
        rateLimiter.check("reset-password", ClientIp.resolve(httpRequest), EMAIL_ACTION_MAX_ATTEMPTS, EMAIL_ACTION_WINDOW_SECONDS);
        String email = authService.resetPassword(request.token(), request.newPassword());
        auditLogService.log(email, AuditAction.PASSWORD_RESET_COMPLETE, "Restablecio su contrasena");
        return ResponseEntity.ok().build();
    }
}
