package com.peluqueria.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Peticion para iniciar el registro: solo requiere el email al que se enviara el enlace. */
public record SignupRequest(
        @NotBlank @Email String email
) {
}
