package com.peluqueria.booking;

import com.peluqueria.audit.AuditAction;
import com.peluqueria.audit.AuditLogService;
import com.peluqueria.booking.dto.BlockView;
import com.peluqueria.booking.dto.CreateBlockRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Endpoints de administracion para gestionar bloqueos manuales de horario
 * (por ejemplo, cerrar un hueco por vacaciones o mantenimiento).
 */
@RestController
@RequestMapping("/api/admin/blocks")
@RequiredArgsConstructor
public class AdminBlockController {

    private final BookingService bookingService;
    private final AuditLogService auditLogService;

    /** Lista los bloqueos de horario existentes en un rango de fechas. */
    @GetMapping
    public List<BlockView> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return bookingService.listBlocks(from, to);
    }

    /** Crea un nuevo bloqueo de horario para impedir reservas en ese rango. */
    @PostMapping
    public BlockView create(@Valid @RequestBody CreateBlockRequest request, Authentication authentication) {
        BlockView view = bookingService.adminCreateBlock(request.date(), request.startTime(), request.endTime(),
                request.reason(), request.workerId());
        auditLogService.log(authentication.getName(), AuditAction.ADMIN_BLOCK_CREATE,
                "Bloqueo horario " + request.date() + " " + request.startTime() + "-" + request.endTime()
                        + " (" + (view.workerName() != null ? view.workerName() : "todo el salon") + ")"
                        + (request.reason() != null ? " - " + request.reason() : ""));
        return view;
    }

    /** Elimina un bloqueo de horario existente, liberando de nuevo ese hueco. */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, Authentication authentication) {
        bookingService.adminDeleteBlock(id);
        auditLogService.log(authentication.getName(), AuditAction.ADMIN_BLOCK_DELETE, "Elimino el bloqueo #" + id);
    }
}
