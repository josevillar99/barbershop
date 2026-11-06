package com.peluqueria.booking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Predicado unico de validez de un candidato de reserva, reutilizado tanto
 * para listar disponibilidad como para re-validar en el momento de reservar.
 * Exige: (1) cada tramo de trabajo candidato cabe entero dentro de un unico
 * intervalo de apertura (un tramo WORK nunca cruza un cierre, solo un WAIT
 * puede), (2) ningun tramo de trabajo candidato solapa lo ya ocupado, y
 * (3) tras anadir tentativamente los tramos candidatos, ningun hueco libre
 * dentro de cada intervalo de apertura queda en una "tierra de nadie": ni
 * lo bastante pequeno para tolerarse como resto inevitable
 * (<= tolerableGapMinutes) ni lo bastante grande para ser util
 * (>= minGapMinutes). Un hueco de 0 (contiguo) siempre es valido.
 */
public final class AvailabilityValidator {

    private AvailabilityValidator() {
    }

    /**
     * Comprueba si un candidato de reserva es valido: cabe dentro del horario
     * de apertura, no se solapa con lo ya ocupado y no deja huecos "inutiles"
     * (ni suficientemente pequenos para tolerarse ni suficientemente grandes para aprovecharse).
     */
    public static boolean isValidCandidate(
            List<OpenInterval> openIntervals,
            List<int[]> occupiedWork,
            List<int[]> candidateWork,
            int minGapMinutes,
            int tolerableGapMinutes) {

        for (int[] work : candidateWork) {
            boolean fitsInSomeInterval = openIntervals.stream()
                    .anyMatch(oi -> work[0] >= oi.startMinutes() && work[1] <= oi.endMinutes());
            if (!fitsInSomeInterval) {
                return false;
            }
        }

        for (int[] candidate : candidateWork) {
            for (int[] occupied : occupiedWork) {
                if (overlaps(candidate, occupied)) {
                    return false;
                }
            }
        }

        List<int[]> merged = new ArrayList<>(occupiedWork);
        merged.addAll(candidateWork);
        merged.sort(Comparator.comparingInt(a -> a[0]));

        for (OpenInterval interval : openIntervals) {
            int cursor = interval.startMinutes();
            for (int[] block : merged) {
                int blockStart = Math.max(block[0], interval.startMinutes());
                int blockEnd = Math.min(block[1], interval.endMinutes());
                if (blockEnd <= interval.startMinutes() || blockStart >= interval.endMinutes()) {
                    continue;
                }
                if (blockStart > cursor) {
                    int gap = blockStart - cursor;
                    if (gap > tolerableGapMinutes && gap < minGapMinutes) {
                        return false;
                    }
                }
                cursor = Math.max(cursor, blockEnd);
            }
            int tailGap = interval.endMinutes() - cursor;
            if (tailGap > tolerableGapMinutes && tailGap < minGapMinutes) {
                return false;
            }
        }

        return true;
    }

    /** Indica si dos intervalos de minutos [inicio, fin) se solapan entre si. */
    private static boolean overlaps(int[] a, int[] b) {
        return a[0] < b[1] && b[0] < a[1];
    }
}
