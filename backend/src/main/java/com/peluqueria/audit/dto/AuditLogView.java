package com.peluqueria.audit.dto;

import com.peluqueria.audit.AuditAction;
import com.peluqueria.audit.AuditLog;

import java.time.Instant;

/** Vista de una fila del log de auditoria expuesta al panel de administracion. */
public record AuditLogView(Long id, String actorEmail, AuditAction action, String details, Instant createdAt) {

    public static AuditLogView from(AuditLog log) {
        return new AuditLogView(log.getId(), log.getActorEmail(), log.getAction(), log.getDetails(), log.getCreatedAt());
    }
}
