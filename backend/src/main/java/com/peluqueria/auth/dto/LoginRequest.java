package com.peluqueria.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Credenciales para iniciar sesion con email y contrasena. */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}
