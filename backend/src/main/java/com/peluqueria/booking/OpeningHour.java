package com.peluqueria.booking;

import com.peluqueria.worker.Worker;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Un tramo de apertura para un dia de la semana concreto
 * (1=lunes .. 7=domingo, como {@link java.time.DayOfWeek#getValue()}) del
 * horario semanal de un trabajador. Un dia puede tener cero, uno o varios
 * tramos (p.ej. manana y tarde). Las filas con {@code worker} nulo son la
 * plantilla heredada del horario unico del salon (ya no editable), usada solo
 * para copiar el horario inicial de cada trabajador nuevo.
 */
@Entity
@Table(name = "business_hours")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpeningHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Convert(converter = LocalTimeMinutesConverter.class)
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Convert(converter = LocalTimeMinutesConverter.class)
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private Worker worker;
}
