package com.peluqueria.user.dto;

/** Datos del usuario que se devuelven al frontend, sin informacion sensible como la contrasena. */
public record UserView(
        Long id,
        String email,
        String role,
        String name,
        String phone,
        boolean profileComplete,
        boolean termsAccepted
) {
}
