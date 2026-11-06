package com.peluqueria.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

/** Datos de un servicio que se devuelven al frontend, incluyendo sus tramos, duracion total y quien lo sabe hacer. */
public record TreatmentView(
        Long id,
        String name,
        boolean active,
        BigDecimal price,
        List<SegmentView> segments,
        int totalMinutes,
        List<Long> workerIds
) {
}
