package com.peluqueria.catalog.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Lista de trabajadores cualificados para realizar un servicio, tal y como se guarda desde el panel de administracion. */
public record UpdateTreatmentWorkersRequest(
        @NotNull List<Long> workerIds
) {
}
