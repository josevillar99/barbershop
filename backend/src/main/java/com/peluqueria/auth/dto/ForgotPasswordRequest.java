package com.peluqueria.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Peticion para iniciar el restablecimiento de contrasena: solo requiere el email al que se enviara el enlace. */
public record ForgotPasswordRequest(
        @NotBlank @Email String email
) {
}
