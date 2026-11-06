package com.peluqueria.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Acceso a datos de los usuarios almacenados en la base de datos. */
public interface UserRepository extends JpaRepository<User, Long> {

    /** Busca un usuario por su email, ignorando mayusculas y minusculas. */
    Optional<User> findByEmailIgnoreCase(String email);

    /** Comprueba si ya existe un usuario con ese email, ignorando mayusculas y minusculas. */
    boolean existsByEmailIgnoreCase(String email);

    /** Busca un usuario por su identificador de cuenta de Google. */
    Optional<User> findByGoogleId(String googleId);
}
