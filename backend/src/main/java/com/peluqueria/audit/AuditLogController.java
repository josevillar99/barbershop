package com.peluqueria.audit;

import com.peluqueria.audit.dto.AuditLogView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint exclusivo del superadministrador para consultar el log de auditoria. */
@RestController
@RequestMapping("/api/superadmin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /** Busca en el log de auditoria, filtrando opcionalmente por email del usuario y por tipo de accion. */
    @GetMapping
    public Page<AuditLogView> search(
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) AuditAction action,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditLogService.search(actorEmail, action, pageable).map(AuditLogView::from);
    }
}
