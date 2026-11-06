package com.peluqueria.cashclosing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Cierre de caja de un dia concreto: efectivo contado fisicamente al terminar la jornada. */
@Entity
@Table(name = "daily_cash_closing")
@Getter
@Setter
@NoArgsConstructor
public class DailyCashClosing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "closing_date", nullable = false, unique = true)
    private LocalDate closingDate;

    @Column(name = "counted_cash", nullable = false, precision = 8, scale = 2)
    private BigDecimal countedCash;

    @Column(name = "closed_by_email", nullable = false)
    private String closedByEmail;

    @Column(name = "closed_at", nullable = false)
    private Instant closedAt;
}
