package com.peluqueria.cashclosing;

import com.peluqueria.audit.AuditAction;
import com.peluqueria.audit.AuditLogService;
import com.peluqueria.cashclosing.dto.CashSummaryView;
import com.peluqueria.cashclosing.dto.SaveCashClosingRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Endpoints de administracion para el cierre de caja diario: cuanto se cobro (segun las citas
 * marcadas como hechas) y cuanto efectivo se contó fisicamente ese dia.
 */
@RestController
@RequestMapping("/api/admin/cash-closing")
@RequiredArgsConstructor
public class CashClosingController {

    private final CashClosingService cashClosingService;
    private final AuditLogService auditLogService;

    /** Resumen de caja de un dia: totales por metodo de pago y, si ya se cerro, el efectivo contado y el descuadre. */
    @GetMapping
    public CashSummaryView getSummary(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return cashClosingService.getSummary(date);
    }

    /** Guarda (o corrige) el cierre de caja de un dia con el efectivo contado fisicamente. */
    @PutMapping
    public CashSummaryView saveClosing(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                        @Valid @RequestBody SaveCashClosingRequest request,
                                        Authentication authentication) {
        CashSummaryView view = cashClosingService.saveClosing(date, request.countedCash(), authentication.getName());
        auditLogService.log(authentication.getName(), AuditAction.ADMIN_CASH_CLOSING_SAVED,
                "Cerro caja del " + date + " (efectivo contado: " + request.countedCash() + "e)");
        return view;
    }
}
