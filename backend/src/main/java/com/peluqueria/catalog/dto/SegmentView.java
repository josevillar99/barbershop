package com.peluqueria.catalog.dto;

import com.peluqueria.catalog.SegmentType;

/** Representa un tramo de tiempo (trabajo o espera) dentro de un servicio, con su duracion en minutos. */
public record SegmentView(
        SegmentType type,
        int minutes
) {
}
