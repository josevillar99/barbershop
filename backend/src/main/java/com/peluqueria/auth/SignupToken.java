package com.peluqueria.auth;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Token temporal enviado por email que permite a un usuario completar su registro.
 */
@Entity
@Table(name = "signup_token")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignupToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    // El token es un valor aleatorio de alta entropia (no un codigo corto
    // adivinable), asi que se guarda en claro y se busca por igualdad directa
    // en vez de hashearlo y comparar contra candidatos por email.
    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;

    // Rol con el que se creara la cuenta al completar el registro: "USER" para
    // el alta normal, "ADMIN" cuando es una invitacion como trabajador.
    @Column(nullable = false)
    private String role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Rellena la fecha de creacion automaticamente al guardar el registro por primera vez. */
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
