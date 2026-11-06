package com.peluqueria.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuracion de la aplicacion leida del fichero de propiedades bajo el prefijo "app"
 * (correo, JWT, administrador, CORS, reservas, frontend, Google y registro).
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Mail mail,
        Jwt jwt,
        Admin admin,
        Superadmin superadmin,
        Cors cors,
        Booking booking,
        Frontend frontend,
        Google google,
        Auth auth,
        Alerts alerts
) {
    /** Direccion de correo que se usa como remitente de los emails de la aplicacion. */
    public record Mail(String from) {
    }

    /** Secreto de firma y duracion de los tokens JWT. */
    public record Jwt(String secret, long expirationMinutes) {
    }

    /** Email que se reconoce como cuenta de administrador. */
    public record Admin(String email) {
    }

    /** Email que se reconoce como cuenta de superadministrador. */
    public record Superadmin(String email) {
    }

    /** Origenes permitidos para las peticiones CORS. */
    public record Cors(List<String> allowedOrigins) {
    }

    /** URL base del frontend, usada para construir enlaces en los emails. */
    public record Frontend(String baseUrl) {
    }

    /** Identificador de cliente OAuth de Google usado para validar el login. */
    public record Google(String clientId) {
    }

    /** Duracion del token de registro y tiempo de espera minimo entre reenvios del enlace. */
    public record Auth(int signupTokenTtlMinutes, int signupResendCooldownSeconds) {
    }

    /** Parametros que gobiernan el calculo de huecos disponibles para reservar cita. */
    public record Booking(int horizonDays, int slotStepMinutes, int tolerableGapMinutes) {
    }

    /** Email al que se avisa por correo cuando ocurre un error inesperado (500) en el backend. */
    public record Alerts(String email) {
    }
}
