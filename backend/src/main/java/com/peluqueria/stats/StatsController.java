package com.peluqueria.stats;

import com.peluqueria.stats.dto.StatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Endpoint exclusivo del superadministrador para consultar las estadisticas de citas. */
@RestController
@RequestMapping("/api/superadmin/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** Devuelve el resumen de citas, el desglose por trabajador y por servicio dentro del rango de fechas indicado. */
    @GetMapping
    public StatsResponse stats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statsService.getStats(from, to);
    }
}
