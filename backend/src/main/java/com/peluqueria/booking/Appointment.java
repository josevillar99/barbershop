package com.peluqueria.booking;

import com.peluqueria.catalog.Segment;
import com.peluqueria.user.User;
import com.peluqueria.worker.Worker;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad que representa una cita reservada (o cancelada) en el sistema,
 * con su fecha, horario, servicios contratados y datos del cliente.
 */
@Entity
@Table(name = "appointment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appt_date", nullable = false)
    private LocalDate date;

    @Convert(converter = LocalTimeMinutesConverter.class)
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Convert(converter = LocalTimeMinutesConverter.class)
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "treatment_id", nullable = false)
    private Long treatmentId;

    @Column(name = "treatment_name", nullable = false)
    private String treatmentName;

    @Column(name = "notes")
    private String notes;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "client_phone", nullable = false)
    private String clientPhone;

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "appointment_segment", joinColumns = @JoinColumn(name = "appointment_id"))
    @OrderColumn(name = "segment_order")
    @BatchSize(size = 50)
    private List<Segment> segments = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    /** Si la cita (ya pasada) se llego a realizar o no; para estadisticas futuras. */
    @Column(nullable = false)
    private boolean completed;

    /** Precio de catalogo de los servicios combinados en el momento de reservar (snapshot, como treatmentName). */
    @Column(name = "expected_price", precision = 8, scale = 2)
    private BigDecimal expectedPrice;

    /** Importe realmente cobrado al marcar la cita como hecha; puede diferir del esperado (descuentos). */
    @Column(name = "amount_charged", precision = 8, scale = 2)
    private BigDecimal amountCharged;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booked_by")
    private User bookedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private Worker worker;

    @Column(name = "booked_at")
    private Instant bookedAt;

    @Version
    private Long version;
}
