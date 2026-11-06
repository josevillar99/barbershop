package com.peluqueria.auth;

import com.peluqueria.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Genera y valida los tokens JWT que identifican la sesion de un usuario autenticado.
 */
@Service
public class JwtService {

    private static final String CREDENTIALS_CLAIM = "cred";

    private final AppProperties appProperties;
    private final SecretKey key;

    /** Crea el servicio y prepara la clave de firma a partir del secreto configurado. */
    public JwtService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.key = Keys.hmacShaKeyFor(appProperties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    /** Genera un token JWT firmado con el email y el rol del usuario, marcado como emitido ahora mismo. */
    public String generateToken(String email, String role) {
        return generateToken(email, role, Instant.now());
    }

    /**
     * Genera un token JWT firmado con el email, el rol y la fecha del ultimo cambio de
     * credenciales del usuario (para poder invalidar tokens emitidos antes de un cambio de
     * contrasena, ver {@link JwtAuthFilter}).
     */
    public String generateToken(String email, String role, Instant credentialsChangedAt) {
        Instant now = Instant.now();
        Instant expiry = now.plus(appProperties.jwt().expirationMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .claim(CREDENTIALS_CLAIM, credentialsChangedAt.toEpochMilli())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /** Valida la firma del token y extrae sus claims (email, rol, fechas). */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Extrae la fecha de cambio de credenciales que llevaba el token en el momento de emitirse. */
    public Instant credentialsChangedAtOf(Claims claims) {
        Long millis = claims.get(CREDENTIALS_CLAIM, Long.class);
        return millis == null ? null : Instant.ofEpochMilli(millis);
    }
}
