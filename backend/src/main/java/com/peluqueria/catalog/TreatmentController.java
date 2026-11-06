package com.peluqueria.catalog;

import com.peluqueria.catalog.dto.TreatmentView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Controlador REST publico para consultar los servicios activos del salon. */
@RestController
@RequestMapping("/api/treatments")
@RequiredArgsConstructor
public class TreatmentController {

    private final TreatmentService treatmentService;

    /** Lista los servicios activos disponibles para reservar. */
    @GetMapping
    public List<TreatmentView> listActive() {
        return treatmentService.listActive();
    }
}
