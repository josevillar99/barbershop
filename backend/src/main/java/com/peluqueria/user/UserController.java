package com.peluqueria.user;

import com.peluqueria.audit.AuditAction;
import com.peluqueria.audit.AuditLogService;
import com.peluqueria.auth.dto.CompleteProfileRequest;
import com.peluqueria.user.dto.ChangePasswordRequest;
import com.peluqueria.user.dto.UserView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Controlador REST con los endpoints para consultar y completar los datos del usuario autenticado. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuditLogService auditLogService;

    /** Devuelve los datos del usuario actualmente autenticado. */
    @GetMapping("/me")
    public UserView me(Authentication authentication) {
        return userService.me(authentication.getName());
    }

    /** Completa el nombre y telefono del perfil del usuario autenticado. */
    @PutMapping("/me/profile")
    public UserView completeProfile(@Valid @RequestBody CompleteProfileRequest request, Authentication authentication) {
        UserView view = userService.completeProfile(authentication.getName(), request.name(), request.phone());
        auditLogService.log(authentication.getName(), AuditAction.PROFILE_UPDATE, "Actualizo su perfil (nombre/telefono)");
        return view;
    }

    /** Cambia la contrasena del usuario autenticado, comprobando antes la contrasena actual. */
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        userService.changePassword(authentication.getName(), request.currentPassword(), request.newPassword());
        auditLogService.log(authentication.getName(), AuditAction.PASSWORD_CHANGED, "Cambio su contrasena desde su perfil");
        return ResponseEntity.ok().build();
    }

    /** Registra que el usuario autenticado ha aceptado los terminos y la politica de privacidad vigentes. */
    @PostMapping("/me/accept-terms")
    public UserView acceptTerms(Authentication authentication) {
        UserView view = userService.acceptTerms(authentication.getName());
        auditLogService.log(authentication.getName(), AuditAction.TERMS_ACCEPTED, "Acepto los terminos y la politica de privacidad");
        return view;
    }
}
