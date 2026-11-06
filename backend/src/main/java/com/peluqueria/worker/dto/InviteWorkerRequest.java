package com.peluqueria.worker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Peticion para invitar a una persona a crear una cuenta de trabajador (rol ADMIN, con perfil de trabajador). */
public record InviteWorkerRequest(
        @NotBlank @Email String email
) {
}
