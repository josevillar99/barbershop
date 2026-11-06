package com.peluqueria.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.peluqueria.auth.dto.AuthResponse;
import com.peluqueria.common.ApiException;
import com.peluqueria.config.AppProperties;
import com.peluqueria.user.Role;
import com.peluqueria.user.RoleAssigner;
import com.peluqueria.user.User;
import com.peluqueria.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Verifica el idToken de Google contra el endpoint tokeninfo de Google. Para
 * un volumen bajo (una peluqueria) esto es suficiente; si el volumen creciera
 * mucho convendria migrar a validacion local del JWT contra las JWKS de
 * Google en vez de depender de este endpoint (que tiene cuota de uso).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final AppProperties appProperties;
    private final AuthService authService;
    private final RoleAssigner roleAssigner;
    private final RestClient restClient = RestClient.create();

    /** Verifica el idToken de Google y crea o actualiza el usuario correspondiente, devolviendo la respuesta de login. */
    @Transactional
    public AuthResponse login(String idToken) {
        GoogleTokenInfo info = verify(idToken);
        String email = info.email().toLowerCase();

        User user = userRepository.findByGoogleId(info.sub())
                .or(() -> userRepository.findByEmailIgnoreCase(email))
                .orElseGet(() -> User.builder().email(email).build());

        user.setGoogleId(info.sub());
        if (user.getRole() == null) {
            user.setRole(roleAssigner.resolveInitialRole(email));
        } else if (user.getRole() != Role.SUPERADMIN && roleAssigner.isSuperadminEmail(email)) {
            user.setRole(Role.SUPERADMIN);
        }
        if (user.getName() == null && info.name() != null) {
            user.setName(info.name());
        }
        userRepository.save(user);

        return authService.toAuthResponse(user);
    }

    /** Llama al endpoint tokeninfo de Google y valida que el token sea autentico, del cliente esperado y con email verificado. */
    private GoogleTokenInfo verify(String idToken) {
        String clientId = appProperties.google().clientId();
        if (clientId == null || clientId.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "El login con Google no esta configurado");
        }

        GoogleTokenInfo info;
        try {
            info = restClient.get()
                    .uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", idToken)
                    .retrieve()
                    .body(GoogleTokenInfo.class);
        } catch (Exception ex) {
            log.warn("Token de Google invalido", ex);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Token de Google invalido");
        }

        if (info == null || info.email() == null || info.sub() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Token de Google invalido");
        }
        if (!clientId.equals(info.aud())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Token de Google invalido");
        }
        if (!"true".equals(info.emailVerified())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "El email de Google no esta verificado");
        }

        return info;
    }

    /** Datos del token de Google devueltos por el endpoint tokeninfo. */
    private record GoogleTokenInfo(
            String sub,
            String email,
            @JsonProperty("email_verified") String emailVerified,
            String name,
            String aud
    ) {
    }
}
