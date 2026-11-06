package com.peluqueria.auth;

import com.peluqueria.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Filtro que lee el token JWT de la cabecera Authorization en cada peticion y, si es valido,
 * establece el usuario autenticado para esa peticion.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    /** Extrae y valida el JWT de la cabecera Authorization, autenticando al usuario si el token es correcto. */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwtService.parseClaims(token);
                String email = claims.getSubject();
                String role = claims.get("role", String.class);

                if (isStale(email, jwtService.credentialsChangedAtOf(claims))) {
                    SecurityContextHolder.clearContext();
                } else {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Un token es obsoleto si el usuario cambio su contrasena despues de que se emitiera:
     * asi un reset de contrasena invalida cualquier token emitido antes, sin necesidad de
     * una lista de revocacion. Si el token no lleva esa marca o el usuario ya no existe, se
     * mantiene el comportamiento anterior (no se rechaza por este motivo).
     * La comparacion trunca a milisegundos porque el claim del token solo guarda esa
     * precision (epoch millis), mientras que el valor en base de datos puede tener mas.
     */
    private boolean isStale(String email, Instant tokenCredentialsChangedAt) {
        if (tokenCredentialsChangedAt == null) {
            return false;
        }
        return userRepository.findByEmailIgnoreCase(email)
                .map(user -> tokenCredentialsChangedAt.isBefore(user.getCredentialsChangedAt().truncatedTo(ChronoUnit.MILLIS)))
                .orElse(false);
    }
}
