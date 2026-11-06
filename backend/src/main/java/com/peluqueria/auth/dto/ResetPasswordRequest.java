package com.peluqueria.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos enviados para fijar una contrasena nueva a partir del token recibido por email. */
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 6) String newPassword
) {
}
