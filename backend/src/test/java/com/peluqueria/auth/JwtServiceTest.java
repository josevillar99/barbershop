package com.peluqueria.auth;

import com.peluqueria.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtServiceTest {

    private static final String SECRET = "a".repeat(64);

    private JwtService serviceWithExpiration(long minutes) {
        AppProperties props = new AppProperties(
                null, new AppProperties.Jwt(SECRET, minutes), null, null, null, null, null, null, null, null);
        return new JwtService(props);
    }

    @Test
    void generaUnTokenQueSeParseaConLosMismosDatos() {
        JwtService service = serviceWithExpiration(60);
        String token = service.generateToken("cliente@example.com", "USER");

        assertNotNull(token);
        Claims claims = service.parseClaims(token);
        assertEquals("cliente@example.com", claims.getSubject());
        assertEquals("USER", claims.get("role", String.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void unTokenCaducadoNoSeAceptaAlParsear() {
        JwtService service = serviceWithExpiration(-1);
        String token = service.generateToken("cliente@example.com", "USER");

        assertThrows(ExpiredJwtException.class, () -> service.parseClaims(token));
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() {
        JwtService serviceA = serviceWithExpiration(60);
        JwtService serviceB = new JwtService(new AppProperties(
                null, new AppProperties.Jwt("b".repeat(64), 60), null, null, null, null, null, null, null, null));

        String tokenFirmadoPorA = serviceA.generateToken("cliente@example.com", "USER");

        assertThrows(SignatureException.class, () -> serviceB.parseClaims(tokenFirmadoPorA));
    }

    @Test
    void unTokenConTextoInvalidoSeRechaza() {
        JwtService service = serviceWithExpiration(60);

        assertThrows(JwtException.class, () -> service.parseClaims("esto-no-es-un-jwt"));
    }

    @Test
    void elTokenLlevaLaFechaDeCambioDeCredencialesIndicada() {
        JwtService service = serviceWithExpiration(60);
        Instant credentialsChangedAt = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        String token = service.generateToken("cliente@example.com", "USER", credentialsChangedAt);
        Claims claims = service.parseClaims(token);

        assertEquals(credentialsChangedAt, service.credentialsChangedAtOf(claims));
    }

    @Test
    void generarTokenSinFechaExplicitaUsaElInstanteActual() {
        JwtService service = serviceWithExpiration(60);
        Instant before = Instant.now();

        String token = service.generateToken("cliente@example.com", "USER");
        Instant credentialsChangedAt = service.credentialsChangedAtOf(service.parseClaims(token));

        assertNotNull(credentialsChangedAt);
        assertFalse(credentialsChangedAt.isBefore(before.truncatedTo(ChronoUnit.SECONDS)));
    }
}
