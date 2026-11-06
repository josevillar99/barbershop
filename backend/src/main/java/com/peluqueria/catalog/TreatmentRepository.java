package com.peluqueria.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Acceso a datos de los servicios almacenados en la base de datos. */
public interface TreatmentRepository extends JpaRepository<Treatment, Long> {

    /** Obtiene todos los servicios marcados como activos. */
    List<Treatment> findByActiveTrue();

    /** Obtiene los servicios que ese trabajador esta cualificado para realizar. */
    List<Treatment> findByWorkersId(Long workerId);
}
