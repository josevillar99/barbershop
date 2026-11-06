package com.peluqueria.worker;

import com.peluqueria.audit.AuditAction;
import com.peluqueria.audit.AuditLogService;
import com.peluqueria.booking.dto.OpeningHourRequest;
import com.peluqueria.booking.dto.OpeningHourView;
import com.peluqueria.worker.dto.UpdateWorkerColorRequest;
import com.peluqueria.worker.dto.WorkerView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Controlador REST de administracion para consultar trabajadores y editar su horario semanal. */
@RestController
@RequestMapping("/api/admin/workers")
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerService workerService;
    private final AuditLogService auditLogService;

    /** Lista todos los trabajadores (activos e inactivos), con su horario y los servicios que saben hacer. */
    @GetMapping
    public List<WorkerView> list() {
        return workerService.listAll();
    }

    /** Sustituye el horario semanal de un trabajador por el enviado desde el panel. */
    @PutMapping("/{id}/hours")
    public List<OpeningHourView> replaceHours(@PathVariable Long id, @Valid @RequestBody List<OpeningHourRequest> requests,
                                               Authentication authentication) {
        List<OpeningHourView> result = workerService.replaceHours(id, requests);
        auditLogService.log(authentication.getName(), AuditAction.WORKER_HOURS_UPDATED, "Actualizo el horario del trabajador #" + id);
        return result;
    }

    /** Cambia el color asignado a un trabajador (debe ser uno de la paleta fija). */
    @PutMapping("/{id}/color")
    public WorkerView updateColor(@PathVariable Long id, @Valid @RequestBody UpdateWorkerColorRequest request,
                                   Authentication authentication) {
        WorkerView view = workerService.updateColor(id, request.color());
        auditLogService.log(authentication.getName(), AuditAction.WORKER_COLOR_UPDATED,
                "Cambio el color del trabajador " + view.name() + " a " + view.color());
        return view;
    }
}
