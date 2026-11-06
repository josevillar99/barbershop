package com.peluqueria.booking;

import com.peluqueria.catalog.Segment;
import com.peluqueria.catalog.SegmentType;
import com.peluqueria.catalog.Treatment;

import java.util.ArrayList;
import java.util.List;

/**
 * Calcula la posicion absoluta (en minutos del dia) de los tramos de TRABAJO
 * de una lista de segmentos, dado un minuto de inicio. Los tramos de ESPERA
 * no ocupan el recurso compartido y no aparecen en el resultado.
 */
public final class SegmentTimeline {

    private SegmentTimeline() {
    }

    /** Calcula los intervalos [inicio, fin) en minutos de cada tramo de trabajo, encadenados desde el inicio dado. */
    public static List<int[]> workIntervals(int startMinutes, List<Segment> segments) {
        List<int[]> result = new ArrayList<>();
        int cursor = startMinutes;
        for (Segment segment : segments) {
            int end = cursor + segment.getMinutes();
            if (segment.getType() == SegmentType.WORK) {
                result.add(new int[]{cursor, end});
            }
            cursor = end;
        }
        return result;
    }

    /** Suma la duracion total en minutos de todos los tramos (trabajo y espera). */
    public static int totalMinutes(List<Segment> segments) {
        return segments.stream().mapToInt(Segment::getMinutes).sum();
    }

    /**
     * Concatena los tramos de varios servicios en el orden dado, para tratar
     * una seleccion de varios servicios (ej. Corte + Peinado) como una sola
     * cita continua.
     */
    public static List<Segment> concat(List<Treatment> treatments) {
        List<Segment> combined = new ArrayList<>();
        for (Treatment treatment : treatments) {
            combined.addAll(treatment.getSegments());
        }
        return combined;
    }
}
