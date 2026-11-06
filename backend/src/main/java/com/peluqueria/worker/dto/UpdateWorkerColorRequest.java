package com.peluqueria.worker.dto;

import jakarta.validation.constraints.NotBlank;

/** Color (hex) elegido para un trabajador, tal y como se guarda desde el panel de administracion. */
public record UpdateWorkerColorRequest(
        @NotBlank String color
) {
}
