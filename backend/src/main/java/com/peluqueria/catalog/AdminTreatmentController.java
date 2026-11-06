package com.peluqueria.catalog;

import com.peluqueria.audit.AuditAction;
import com.peluqueria.audit.AuditLogService;
import com.peluqueria.catalog.dto.TreatmentRequest;
import com.peluqueria.catalog.dto.TreatmentView;
import com.peluqueria.catalog.dto.UpdateTreatmentWorkersRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Controlador REST de administracion para crear, editar y listar todos los servicios (activos e inactivos). */
@RestController
@RequestMapping("/api/admin/treatments")
@RequiredArgsConstructor
public class AdminTreatmentController {

    private final TreatmentService treatmentService;
    private final AuditLogService auditLogService;

    /** Lista todos los servicios, tanto activos como inactivos. */
    @GetMapping
    public List<TreatmentView> listAll() {
        return treatmentService.listAll();
    }

    /** Crea un nuevo servicio con sus tramos de tiempo y precio. */
    @PostMapping
    public TreatmentView create(@Valid @RequestBody TreatmentRequest request, Authentication authentication) {
        TreatmentView view = treatmentService.create(request);
        auditLogService.log(authentication.getName(), AuditAction.ADMIN_TREATMENT_CREATE, "Creo el servicio '" + request.name() + "'");
        return view;
    }

    /** Actualiza los datos de un servicio existente identificado por su id. */
    @PutMapping("/{id}")
    public TreatmentView update(@PathVariable Long id, @Valid @RequestBody TreatmentRequest request, Authentication authentication) {
        TreatmentView view = treatmentService.update(id, request);
        auditLogService.log(authentication.getName(), AuditAction.ADMIN_TREATMENT_UPDATE, "Actualizo el servicio '" + request.name() + "'");
        return view;
    }

    /** Sustituye la lista de trabajadores cualificados para realizar un servicio. */
    @PutMapping("/{id}/workers")
    public TreatmentView updateWorkers(@PathVariable Long id, @Valid @RequestBody UpdateTreatmentWorkersRequest request,
                                        Authentication authentication) {
        TreatmentView view = treatmentService.replaceWorkers(id, request.workerIds());
        auditLogService.log(authentication.getName(), AuditAction.TREATMENT_WORKERS_UPDATED,
                "Actualizo quien puede hacer el servicio '" + view.name() + "'");
        return view;
    }
}
