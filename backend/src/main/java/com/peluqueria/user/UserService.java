package com.peluqueria.user;

import com.peluqueria.booking.AppointmentRepository;
import com.peluqueria.common.ApiException;
import com.peluqueria.user.dto.UserView;
import com.peluqueria.worker.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Logica de negocio relacionada con la consulta y actualizacion de datos de usuario. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final WorkerService workerService;
    private final PasswordEncoder passwordEncoder;

    /** Obtiene los datos del usuario identificado por su email. */
    @Transactional(readOnly = true)
    public UserView me(String email) {
        return toView(getUser(email));
    }

    /** Actualiza el nombre y telefono del usuario para completar su perfil. */
    @Transactional
    public UserView completeProfile(String email, String name, String phone) {
        User user = getUser(email);
        user.setName(name);
        user.setPhone(phone);
        return toView(user);
    }

    /** Cambia la contrasena del usuario autenticado, comprobando antes que conoce la contrasena actual. */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = getUser(email);
        if (user.getPasswordHash() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tu cuenta no tiene contrasena (inicias sesion con Google)");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La contrasena actual no es correcta");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setCredentialsChangedAt(Instant.now());
    }

    /** Registra que el usuario autenticado ha aceptado los terminos y la politica de privacidad vigentes. */
    @Transactional
    public UserView acceptTerms(String email) {
        User user = getUser(email);
        user.setTermsAcceptedAt(Instant.now());
        return toView(user);
    }

    /** Lista todos los usuarios registrados, ordenados por email, para el panel de superadministrador. */
    @Transactional(readOnly = true)
    public List<UserView> listAll() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "email")).stream().map(this::toView).toList();
    }

    /** Asciende a un usuario normal a administrador; no toca cuentas que ya sean ADMIN o SUPERADMIN. */
    @Transactional
    public UserView makeAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (user.getRole() == Role.SUPERADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ese usuario ya es superadministrador");
        }
        if (user.getRole() != Role.ADMIN) {
            user.setRole(Role.ADMIN);
        }
        return toView(user);
    }

    /** Quita el rol de administrador a un usuario, dejandolo como cliente normal; solo actua sobre cuentas ADMIN. */
    @Transactional
    public UserView removeAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (user.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ese usuario no es administrador");
        }
        user.setRole(Role.USER);
        return toView(user);
    }

    /**
     * Elimina la cuenta de un usuario; sus citas pasadas o futuras no se
     * borran, quedan sin dueño (ya conservan el nombre/telefono del cliente).
     * No permite que el superadministrador se elimine a si mismo.
     * Devuelve el email del usuario eliminado, para poder registrarlo en el log de auditoria.
     */
    @Transactional
    public String deleteUser(Long userId, String requesterEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        if (user.getEmail().equalsIgnoreCase(requesterEmail)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No puedes eliminar tu propia cuenta");
        }
        String deletedEmail = user.getEmail();
        appointmentRepository.detachBookedBy(userId);
        workerService.deleteForUser(userId);
        userRepository.delete(user);
        return deletedEmail;
    }

    /** Busca el usuario por email o lanza un error si no esta autenticado correctamente. */
    private User getUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }

    /** Convierte la entidad User en el DTO que se expone al frontend, indicando si el perfil esta completo. */
    private UserView toView(User user) {
        boolean profileComplete = user.getName() != null && user.getPhone() != null;
        return new UserView(user.getId(), user.getEmail(), user.getRole().name(), user.getName(), user.getPhone(),
                profileComplete, user.getTermsAcceptedAt() != null);
    }
}
