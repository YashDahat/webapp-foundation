package com.webappfoundation.config;

import com.webappfoundation.model.User;
import com.webappfoundation.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T5 / finding #5 — the SUPER_ADMIN must never be seeded with a blank or known-default password
 * unless the dev-only escape hatch (admin.allow-insecure-default=true) is set.
 */
class AdminInitializerSeedGuardTest {

    private AdminInitializer initializer(String password, boolean allowInsecureDefault) {
        UserRepository repo = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(repo.findByEmail(any())).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("hashed");
        AdminInitializer init = new AdminInitializer(repo, encoder);
        ReflectionTestUtils.setField(init, "adminEmail", "owner@example.com");
        ReflectionTestUtils.setField(init, "adminPhone", "+910000000000");
        ReflectionTestUtils.setField(init, "adminPassword", password);
        ReflectionTestUtils.setField(init, "allowInsecureDefault", allowInsecureDefault);
        return init;
    }

    @Test
    void refusesKnownDefaultPassword() {
        assertThatThrownBy(() -> initializer("changeme123", false).run())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesBlankPassword() {
        assertThatThrownBy(() -> initializer("", false).run())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsDefaultOnlyWithDevEscapeHatch() {
        UserRepository repo = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(repo.findByEmail(any())).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("hashed");
        AdminInitializer init = new AdminInitializer(repo, encoder);
        ReflectionTestUtils.setField(init, "adminEmail", "owner@example.com");
        ReflectionTestUtils.setField(init, "adminPhone", "+910000000000");
        ReflectionTestUtils.setField(init, "adminPassword", "changeme123");
        ReflectionTestUtils.setField(init, "allowInsecureDefault", true);

        assertThatCode(() -> init.run()).doesNotThrowAnyException();
        verify(repo).save(any(User.class));
    }
}
