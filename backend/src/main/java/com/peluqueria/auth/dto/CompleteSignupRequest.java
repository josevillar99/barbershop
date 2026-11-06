package com.peluqueria.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos enviados para completar el registro a partir del token recibido por email. */
public record CompleteSignupRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 6) String password,
        @NotBlank String name,
        @NotBlank String phone
) {
}
