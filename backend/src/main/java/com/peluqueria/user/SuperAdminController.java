package com.peluqueria.user;

import com.peluqueria.audit.AuditAction;
import com.peluqueria.audit.AuditLogService;
import com.peluqueria.auth.AuthService;
import com.peluqueria.user.dto.UserView;
import com.peluqueria.worker.WorkerService;
import com.peluqueria.worker.dto.InviteWorkerRequest;
import com.peluqueria.worker.dto.WorkerView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Endpoints exclusivos del superadministrador: ver todos los usuarios, ascender alguno a administrador y gestionar trabajadores. */
@RestController
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
public class SuperAdminController {

    private final UserService userService;
    private final AuditLogService auditLogService;
    private final AuthService authService;
    private final WorkerService workerService;

    /** Lista todos los usuarios registrados. */
    @GetMapping("/users")
    public List<UserView> listUsers() {
        return userService.listAll();
    }

    /** Asciende al usuario indicado al rol de administrador. */
    @PostMapping("/users/{id}/make-admin")
    public UserView makeAdmin(@PathVariable Long id, Authentication authentication) {
        UserView view = userService.makeAdmin(id);
        auditLogService.log(authentication.getName(), AuditAction.SUPERADMIN_MAKE_ADMIN, "Ascendio a admin a " + view.email());
        return view;
    }

    /** Quita el rol de administrador al usuario indicado, dejandolo como cliente. */
    @PostMapping("/users/{id}/remove-admin")
    public UserView removeAdmin(@PathVariable Long id, Authentication authentication) {
        UserView view = userService.removeAdmin(id);
        auditLogService.log(authentication.getName(), AuditAction.SUPERADMIN_REMOVE_ADMIN, "Quito el rol de admin a " + view.email());
        return view;
    }

    /** Elimina la cuenta de un usuario; no permite eliminar la propia cuenta del que hace la peticion. */
    @DeleteMapping("/users/{id}")
    public void deleteUser(@PathVariable Long id, Authentication authentication) {
        String deletedEmail = userService.deleteUser(id, authentication.getName());
        auditLogService.log(authentication.getName(), AuditAction.SUPERADMIN_DELETE_USER, "Elimino la cuenta de " + deletedEmail);
    }

    /** Invita por email a una persona a crear una cuenta de trabajador (rol ADMIN con perfil de trabajador). */
    @PostMapping("/workers/invite")
    public ResponseEntity<Void> inviteWorker(@Valid @RequestBody InviteWorkerRequest request, Authentication authentication) {
        authService.inviteWorker(request.email());
        auditLogService.log(authentication.getName(), AuditAction.WORKER_INVITED, "Invito a " + request.email() + " como trabajador");
        return ResponseEntity.accepted().build();
    }

    /** Convierte a un usuario ya existente en trabajador (le asigna rol ADMIN si no lo tenia y le crea el perfil de trabajador). */
    @PostMapping("/workers/{userId}/promote")
    public WorkerView promoteToWorker(@PathVariable Long userId, Authentication authentication) {
        WorkerView view = workerService.promoteExistingUser(userId);
        auditLogService.log(authentication.getName(), AuditAction.WORKER_PROMOTED, "Convirtio en trabajador a " + view.email());
        return view;
    }

    /** Quita el perfil de trabajador a alguien, sin borrar su cuenta ni tocar su rol. */
    @DeleteMapping("/workers/{workerId}")
    public void removeWorkerProfile(@PathVariable Long workerId, Authentication authentication) {
        String workerName = workerService.removeWorkerProfile(workerId);
        auditLogService.log(authentication.getName(), AuditAction.WORKER_PROFILE_REMOVED, "Quito el perfil de trabajador a " + workerName);
    }
}
