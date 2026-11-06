package com.peluqueria.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Acceso a datos de los tokens de registro pendientes de completar.
 */
public interface SignupTokenRepository extends JpaRepository<SignupToken, Long> {

    /** Busca un token de registro por su valor unico. */
    Optional<SignupToken> findByToken(String token);

    /** Busca el token de registro mas reciente de un email creado despues de una fecha, para el control de cooldown de reenvios. */
    Optional<SignupToken> findFirstByEmailIgnoreCaseAndCreatedAtAfterOrderByCreatedAtDesc(String email, Instant after);
}
