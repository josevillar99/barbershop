package com.peluqueria.stats.dto;

import java.util.List;

/** Respuesta completa del panel de estadisticas para un rango de fechas. */
public record StatsResponse(
        StatsSummaryView summary,
        List<WorkerStatsView> byWorker,
        List<TreatmentStatsView> byTreatment
) {
}
