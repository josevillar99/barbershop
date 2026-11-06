package com.peluqueria.catalog;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tramo de tiempo embebido dentro de un servicio (Treatment), con su tipo y duracion en minutos. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Segment {

    @Enumerated(EnumType.STRING)
    private SegmentType type;

    private int minutes;
}
