package com.peluqueria.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Peticion de login con Google: contiene el idToken emitido por Google. */
public record GoogleLoginRequest(
        @NotBlank String idToken
) {
}
