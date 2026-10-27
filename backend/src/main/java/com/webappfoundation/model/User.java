package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_user")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String firstName;
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    // Primary contact + alternate login identity (India-first: phone over email). Required + unique.
    @Column(nullable = false, unique = true)
    private String phone;

    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    // Set true once the user verifies the OTP emailed at signup. Login is blocked until verified.
    @Builder.Default
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean emailVerified = false;

    // ── Email-OTP two-factor auth (opt-in per user; otp* fields are server-internal) ──
    // ColumnDefault backfills these NOT NULL columns when the schema is updated on an existing table.
    @Builder.Default
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean twoFactorEnabled = false;
    /** BCrypt hash of the current login OTP (never store the plaintext code). */
    private String otpCodeHash;
    private Instant otpExpiresAt;
    /** Failed verification attempts for the current code; the code is locked after the max. */
    @Builder.Default
    @ColumnDefault("0")
    @Column(nullable = false)
    private int otpAttempts = 0;

    /**
     * Monotonic credential version embedded in issued JWTs (claim {@code tv}). A request is rejected
     * when its token's {@code tv} does not match this value, so bumping it on a password change/reset
     * revokes every previously-issued token (and makes a used reset link single-use). Server-internal.
     */
    @Builder.Default
    @ColumnDefault("0")
    @Column(nullable = false)
    private long tokenVersion = 0;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
