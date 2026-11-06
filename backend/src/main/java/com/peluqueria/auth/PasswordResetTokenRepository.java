package com.peluqueria.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Acceso a datos de los tokens de restablecimiento de contrasena pendientes de usar.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** Busca un token de restablecimiento por su valor unico. */
    Optional<PasswordResetToken> findByToken(String token);

    /** Busca el token mas reciente de un email creado despues de una fecha, para el control de cooldown de reenvios. */
    Optional<PasswordResetToken> findFirstByEmailIgnoreCaseAndCreatedAtAfterOrderByCreatedAtDesc(String email, Instant after);
}
