package com.peluqueria.cashclosing;

import com.peluqueria.booking.BookingService;
import com.peluqueria.booking.PaymentMethod;
import com.peluqueria.booking.dto.AdminAppointmentView;
import com.peluqueria.cashclosing.dto.CashSummaryView;
import com.peluqueria.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Calcula el resumen de caja de un dia (totales por metodo de pago) y guarda su cierre (efectivo contado). */
@Service
@RequiredArgsConstructor
public class CashClosingService {

    private final BookingService bookingService;
    private final DailyCashClosingRepository closingRepository;

    @Transactional(readOnly = true)
    public CashSummaryView getSummary(LocalDate date) {
        List<AdminAppointmentView> appointments = bookingService.listCompletedForCashClosing(date);
        BigDecimal efectivo = sumByMethod(appointments, PaymentMethod.EFECTIVO);
        BigDecimal tarjeta = sumByMethod(appointments, PaymentMethod.TARJETA);
        BigDecimal otro = sumByMethod(appointments, PaymentMethod.OTRO);
        BigDecimal total = efectivo.add(tarjeta).add(otro);

        DailyCashClosing closing = closingRepository.findByClosingDate(date).orElse(null);
        BigDecimal countedCash = closing != null ? closing.getCountedCash() : null;
        BigDecimal difference = countedCash != null ? countedCash.subtract(efectivo) : null;

        return new CashSummaryView(date, efectivo, tarjeta, otro, total, appointments.size(), appointments,
                countedCash, difference);
    }

    @Transactional
    public CashSummaryView saveClosing(LocalDate date, BigDecimal countedCash, String closedByEmail) {
        if (countedCash == null || countedCash.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Indica el efectivo contado");
        }
        DailyCashClosing closing = closingRepository.findByClosingDate(date).orElseGet(() -> {
            DailyCashClosing c = new DailyCashClosing();
            c.setClosingDate(date);
            return c;
        });
        closing.setCountedCash(countedCash);
        closing.setClosedByEmail(closedByEmail);
        closing.setClosedAt(Instant.now());
        closingRepository.save(closing);
        return getSummary(date);
    }

    private BigDecimal sumByMethod(List<AdminAppointmentView> appointments, PaymentMethod method) {
        return appointments.stream()
                .filter(a -> a.paymentMethod() == method)
                .map(AdminAppointmentView::amountCharged)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
