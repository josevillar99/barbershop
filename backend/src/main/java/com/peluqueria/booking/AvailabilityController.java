package com.peluqueria.booking;

import com.peluqueria.booking.dto.AvailabilityDayView;
import com.peluqueria.booking.dto.OccupiedDayView;
import com.peluqueria.worker.dto.AvailableWorkerView;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Endpoints publicos para consultar horas libres y franjas ya ocupadas.
 */
@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    /** Devuelve, por cada dia del rango, las horas de inicio disponibles para los servicios elegidos. */
    @GetMapping
    public List<AvailabilityDayView> availability(
            @RequestParam List<Long> treatmentIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return availabilityService.availability(treatmentIds, from, to);
    }

    /** Devuelve, por cada dia del rango, las franjas horarias ya ocupadas por reservas existentes. */
    @GetMapping("/occupied")
    public List<OccupiedDayView> occupied(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return availabilityService.occupied(from, to);
    }

    /** Devuelve los trabajadores cualificados para esos servicios que estan libres exactamente en esa fecha y hora. */
    @GetMapping("/workers")
    public List<AvailableWorkerView> availableWorkers(
            @RequestParam List<Long> treatmentIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String startTime) {
        return availabilityService.availableWorkers(treatmentIds, date, startTime);
    }
}
