package com.peluqueria.booking;

import com.peluqueria.catalog.Segment;
import com.peluqueria.catalog.SegmentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilityValidatorTest {

    // Horario tipo BusinessHours: manana 9:00-14:00, tarde 16:00-20:00 (cierre de 840 a 960).
    private static final List<OpenInterval> BUSINESS_HOURS = List.of(
            new OpenInterval(9 * 60, 14 * 60),
            new OpenInterval(16 * 60, 20 * 60)
    );

    @Test
    void unTramoDeEsperaPuedeSaltarUnCierre() {
        // WORK 30 + WAIT 120 (exactamente el cierre de 14:00-16:00) + WORK 30.
        // El primer WORK termina justo al cierre (13:30-14:00) y el segundo
        // empieza justo al reabrir (16:00-16:30): ningun hueco residual,
        // asi que solo se pone a prueba que el WAIT puede saltar el cierre.
        List<Segment> segments = List.of(
                new Segment(SegmentType.WORK, 30),
                new Segment(SegmentType.WAIT, 120),
                new Segment(SegmentType.WORK, 30)
        );
        int start = 13 * 60 + 30; // 13:30
        List<int[]> candidateWork = SegmentTimeline.workIntervals(start, segments);

        assertTrue(AvailabilityValidator.isValidCandidate(BUSINESS_HOURS, List.of(), candidateWork, 45, 0));
    }

    @Test
    void unTramoDeTrabajoNuncaCruzaUnCierreAunqueEncajariaTrasReabrir() {
        // Un solo tramo WORK de 90 min empezando a las 13:00 terminaria a las 14:30,
        // cruzando el cierre de 14:00-16:00. No debe permitirse aunque el resto
        // "encajaria" tras la reapertura.
        List<Segment> segments = List.of(new Segment(SegmentType.WORK, 90));
        int start = 13 * 60;
        List<int[]> candidateWork = SegmentTimeline.workIntervals(start, segments);

        assertFalse(AvailabilityValidator.isValidCandidate(BUSINESS_HOURS, List.of(), candidateWork, 45, 0));
    }

    @Test
    void rechazaHuecoMedianoPeroPermiteContiguoYSuficiente() {
        List<Segment> segments = List.of(new Segment(SegmentType.WORK, 20));
        int minGap = 45;
        int tolerableGap = 15;
        // Cita ya existente: 10:00-10:50 (600-650)
        List<int[]> occupied = List.of(new int[]{600, 650});

        // Candidato a las 11:15 (675) deja un hueco de 25 min: ni tolerable (>15)
        // ni suficiente (<45): invalido.
        List<int[]> mediumGapCandidate = SegmentTimeline.workIntervals(675, segments);
        assertFalse(AvailabilityValidator.isValidCandidate(
                BUSINESS_HOURS, occupied, mediumGapCandidate, minGap, tolerableGap));

        // Candidato justo a continuacion (650), hueco = 0: valido.
        List<int[]> contiguousCandidate = SegmentTimeline.workIntervals(650, segments);
        assertTrue(AvailabilityValidator.isValidCandidate(
                BUSINESS_HOURS, occupied, contiguousCandidate, minGap, tolerableGap));

        // Candidato dejando exactamente el hueco minimo (695 = 650+45): valido.
        List<int[]> exactGapCandidate = SegmentTimeline.workIntervals(695, segments);
        assertTrue(AvailabilityValidator.isValidCandidate(
                BUSINESS_HOURS, occupied, exactGapCandidate, minGap, tolerableGap));
    }

    @Test
    void toleraUnRestoPequenoInevitableAunquePorDebajoDelMinimo() {
        // Caso real reportado: Barba 9:00-9:20 (540-560) y Mechas 10:00-12:00
        // (600-720, con hueco de espera 630-690). Un Peinado de 30 min no cabe
        // en el hueco de 40 min (9:20-10:00) sin dejar un resto de 10 min, por
        // debajo del minimo activo (20, Barba). Con una tolerancia de 15 min,
        // ese resto de 10 min debe aceptarse.
        List<Segment> segments = List.of(new Segment(SegmentType.WORK, 30));
        int minGap = 20;
        int tolerableGap = 15;
        List<int[]> occupied = List.of(new int[]{540, 560}, new int[]{600, 630}, new int[]{630, 650}, new int[]{690, 720});

        // Empezar justo tras la Barba (560): deja un resto de 10 min antes de Mechas (600).
        List<int[]> rightAfterBarba = SegmentTimeline.workIntervals(560, segments);
        assertTrue(AvailabilityValidator.isValidCandidate(
                BUSINESS_HOURS, occupied, rightAfterBarba, minGap, tolerableGap));

        // Terminar justo cuando empieza Mechas (inicio 570): deja un resto de 10 min tras la Barba.
        List<int[]> rightBeforeMechas = SegmentTimeline.workIntervals(570, segments);
        assertTrue(AvailabilityValidator.isValidCandidate(
                BUSINESS_HOURS, occupied, rightBeforeMechas, minGap, tolerableGap));
    }
}
