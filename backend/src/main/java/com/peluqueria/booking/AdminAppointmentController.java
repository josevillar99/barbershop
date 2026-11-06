package com.peluqueria.booking;

import com.peluqueria.audit.AuditAction;
import com.peluqueria.audit.AuditLogService;
import com.peluqueria.booking.dto.AdminAppointmentView;
import com.peluqueria.booking.dto.AdminBookRequest;
import com.peluqueria.booking.dto.UpdateCompletedRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Endpoints de administracion para gestionar las citas de todos los clientes.
 */
@RestController
@RequestMapping("/api/admin/appointments")
@RequiredArgsConstructor
public class AdminAppointmentController {

    private final BookingService bookingService;
    private final AuditLogService auditLogService;

    /** Lista todas las citas reservadas en un rango de fechas, para el panel de admin. */
    @GetMapping
    public List<AdminAppointmentView> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return bookingService.listForAdmin(from, to);
    }

    /** Crea una cita en nombre de un cliente (por ejemplo, reservada por telefono). */
    @PostMapping
    public AdminAppointmentView book(@Valid @RequestBody AdminBookRequest request, Authentication authentication) {
        AdminAppointmentView view = bookingService.adminBook(request.treatmentIds(), request.date(), request.startTime(),
                request.notes(), request.clientName(), request.clientPhone(), request.workerId());
        auditLogService.log(authentication.getName(), AuditAction.ADMIN_APPOINTMENT_BOOK,
                "Creo " + view.treatmentName() + " con " + view.workerName() + " para " + request.clientName()
                        + " (" + request.clientPhone() + ") el " + request.date() + " de " + view.startTime() + " a " + view.endTime());
        return view;
    }

    /** Cancela cualquier cita por su id, sin restringir por propietario; falla si la cita ya ha pasado. */
    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable Long id, Authentication authentication) {
        bookingService.adminCancel(id);
        auditLogService.log(authentication.getName(), AuditAction.ADMIN_APPOINTMENT_CANCEL, "Cancelo la cita #" + id);
    }

    /** Marca (o desmarca) si una cita ya pasada se llego a realizar, para futuras estadisticas. */
    @PutMapping("/{id}/completed")
    public AdminAppointmentView setCompleted(@PathVariable Long id, @Valid @RequestBody UpdateCompletedRequest request,
                                              Authentication authentication) {
        AdminAppointmentView view = bookingService.setCompleted(id, request.completed(), request.amountCharged(),
                request.paymentMethod());
        String detail = request.completed()
                ? "Marco la cita #" + id + " como hecha (" + request.amountCharged() + "e, " + request.paymentMethod() + ")"
                : "Marco la cita #" + id + " como no hecha";
        auditLogService.log(authentication.getName(), AuditAction.ADMIN_APPOINTMENT_COMPLETED_UPDATED, detail);
        return view;
    }
}
