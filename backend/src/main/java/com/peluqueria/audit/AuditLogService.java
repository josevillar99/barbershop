package com.peluqueria.audit;

import com.peluqueria.common.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Logica de registro y consulta del log de auditoria. */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Registra una accion realizada por un usuario, añadiendo a la descripcion la IP
     * desde la que se hizo (si el registro ocurre dentro de una peticion HTTP; en
     * llamadas internas sin peticion asociada no se añade nada).
     */
    @Transactional
    public void log(String actorEmail, AuditAction action, String details) {
        String ip = currentClientIp();
        String detailsWithIp = ip == null ? details : details + " (IP: " + ip + ")";
        auditLogRepository.save(AuditLog.builder()
                .actorEmail(actorEmail)
                .action(action)
                .details(detailsWithIp)
                .build());
    }

    /** Obtiene la IP del cliente de la peticion HTTP en curso, o null si no hay ninguna (ej. tarea programada). */
    private String currentClientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        return ClientIp.resolve(request);
    }

    /** Busca en el log de auditoria, filtrando opcionalmente por email del actor (parcial) y por accion exacta. */
    @Transactional(readOnly = true)
    public Page<AuditLog> search(String actorEmail, AuditAction action, Pageable pageable) {
        return auditLogRepository.search(actorEmail, action, pageable);
    }
}
