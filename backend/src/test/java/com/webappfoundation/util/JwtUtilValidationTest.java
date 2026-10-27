package com.webappfoundation.util;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T2 / finding #2 — the app must fail fast rather than run with a missing or weak JWT signing key.
 * Unit test (no Spring context): a blank or sub-32-byte secret throws; a 32+ byte secret is accepted.
 */
class JwtUtilValidationTest {

    private JwtUtil withSecret(String secret) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", secret);
        ReflectionTestUtils.setField(util, "expirationMs", 86_400_000L);
        return util;
    }

    @Test
    void blankSecret_isRejected() {
        assertThatThrownBy(() -> withSecret("").validateSigningKey())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shortSecret_isRejected() {
        assertThatThrownBy(() -> withSecret("too-short-key").validateSigningKey())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void strongSecret_isAccepted() {
        assertThatCode(() -> withSecret("a-sufficiently-long-signing-key-32bytes!").validateSigningKey())
                .doesNotThrowAnyException();
    }
}
