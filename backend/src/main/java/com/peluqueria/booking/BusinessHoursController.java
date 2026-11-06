package com.peluqueria.booking;

import com.peluqueria.booking.dto.OpeningHourView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Controlador REST para que cualquier usuario autenticado consulte el horario de apertura configurado. */
@RestController
@RequestMapping("/api/business-hours")
@RequiredArgsConstructor
public class BusinessHoursController {

    private final BusinessHours businessHours;

    /** Union de horarios de todos los trabajadores activos, para pintar el esqueleto del calendario. */
    @GetMapping
    public List<OpeningHourView> list() {
        return businessHours.listUnionOfActiveWorkers();
    }
}
