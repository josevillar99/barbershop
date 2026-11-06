package com.peluqueria.user;

import com.peluqueria.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decide el rol inicial de una cuenta nueva segun su email, comparandolo con
 * los emails de superadministrador y administrador configurados.
 */
@Component
@RequiredArgsConstructor
public class RoleAssigner {

    private final AppProperties appProperties;

    /** Devuelve SUPERADMIN o ADMIN si el email coincide con el configurado para ese rol, o USER en caso contrario. */
    public Role resolveInitialRole(String email) {
        if (email.equalsIgnoreCase(appProperties.superadmin().email())) {
            return Role.SUPERADMIN;
        }
        if (email.equalsIgnoreCase(appProperties.admin().email())) {
            return Role.ADMIN;
        }
        return Role.USER;
    }

    /** Indica si ese email es el configurado como superadministrador. */
    public boolean isSuperadminEmail(String email) {
        return email.equalsIgnoreCase(appProperties.superadmin().email());
    }
}
