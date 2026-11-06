package com.peluqueria.auth.dto;

/** Datos devueltos tras un login o registro correcto: token JWT y datos basicos del usuario. */
public record AuthResponse(String token, String email, String name, String role, boolean profileComplete, boolean termsAccepted) {
}
