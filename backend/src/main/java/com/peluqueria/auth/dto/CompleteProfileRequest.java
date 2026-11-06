package com.peluqueria.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Datos para completar el perfil de un usuario (nombre y telefono). */
public record CompleteProfileRequest(
        @NotBlank String name,
        @NotBlank String phone
) {
}
