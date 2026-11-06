package com.peluqueria.worker;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Acceso a datos de los perfiles de trabajador. */
public interface WorkerRepository extends JpaRepository<Worker, Long> {

    /** Busca el perfil de trabajador ligado a esa cuenta de usuario, si tiene uno. */
    Optional<Worker> findByUserId(Long userId);

    /** Trabajadores activos, ordenados por nombre, para elegirlos al reservar o listarlos en el admin. */
    List<Worker> findByActiveTrueOrderByUserNameAsc();
}
