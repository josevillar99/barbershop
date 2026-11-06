package com.peluqueria.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a datos del log de auditoria, con busqueda paginada filtrando opcionalmente por usuario y accion. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // El orden lo aplica Spring Data a partir del Sort del Pageable (ver AuditLogController):
    // un "order by" fijo aqui chocaria con el que se anade automaticamente y rompe la consulta.
    // El cast a "string" evita que Postgres no sepa resolver el tipo del parametro dentro de
    // concat/lower cuando actorEmail es null (sin el cast, PG intenta usar lower(bytea) y falla).
    @Query("select a from AuditLog a where "
            + "(:actorEmail is null or lower(a.actorEmail) like lower(concat('%', cast(:actorEmail as string), '%'))) "
            + "and (:action is null or a.action = :action)")
    Page<AuditLog> search(@Param("actorEmail") String actorEmail, @Param("action") AuditAction action, Pageable pageable);
}
