package com.peluqueria.booking;

import com.peluqueria.audit.AuditAction;
import com.peluqueria.audit.AuditLogService;
import com.peluqueria.booking.dto.AppointmentHistoryView;
import com.peluqueria.booking.dto.AppointmentView;
import com.peluqueria.booking.dto.BookRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Endpoints publicos para que un cliente autenticado gestione sus propias citas.
 */
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final BookingService bookingService;
    private final AuditLogService auditLogService;

    /** Lista las citas propias del usuario autenticado en un rango de fechas. */
    @GetMapping
    public List<AppointmentView> listOwn(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        return bookingService.listOwn(from, to, authentication.getName());
    }

    /** Historico completo (reservadas y canceladas) de citas del usuario autenticado, para su pagina de perfil. */
    @GetMapping("/history")
    public List<AppointmentHistoryView> history(Authentication authentication) {
        return bookingService.history(authentication.getName());
    }

    /** Crea una nueva reserva para el usuario autenticado. */
    @PostMapping
    public AppointmentView book(@Valid @RequestBody BookRequest request, Authentication authentication) {
        AppointmentView view = bookingService.book(request.treatmentIds(), request.date(), request.startTime(), request.notes(),
                request.workerId(), authentication.getName());
        auditLogService.log(authentication.getName(), AuditAction.APPOINTMENT_BOOK,
                "Reservo " + view.treatmentName() + " con " + view.workerName() + " el " + request.date()
                        + " de " + view.startTime() + " a " + view.endTime());
        return view;
    }

    /** Cancela una cita propia del usuario autenticado. */
    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable Long id, Authentication authentication) {
        bookingService.cancelOwn(id, authentication.getName());
        auditLogService.log(authentication.getName(), AuditAction.APPOINTMENT_CANCEL, "Cancelo la cita #" + id);
    }
}
