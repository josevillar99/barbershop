package com.peluqueria.cashclosing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyCashClosingRepository extends JpaRepository<DailyCashClosing, Long> {

    Optional<DailyCashClosing> findByClosingDate(LocalDate closingDate);
}
