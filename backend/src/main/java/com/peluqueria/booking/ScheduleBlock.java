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

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Entidad que representa un bloqueo manual de horario creado por el
 * administrador (por ejemplo, vacaciones o mantenimiento), impidiendo
 * reservas en ese rango. Si {@code worker} es nulo, el bloqueo es de todo el
 * salon; si no, solo afecta a ese trabajador.
 */
@Entity
@Table(name = "schedule_block")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_date", nullable = false)
    private LocalDate date;

    @Convert(converter = LocalTimeMinutesConverter.class)
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Convert(converter = LocalTimeMinutesConverter.class)
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "reason")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private Worker worker;
}
