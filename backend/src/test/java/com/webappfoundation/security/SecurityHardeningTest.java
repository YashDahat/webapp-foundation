package com.webappfoundation.security;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Automated checks for the Security Hardening Plan (see {@code /security_hardening_plan.md} — "Security
 * test matrix"). Each test encodes the TARGET (post-fix, secure) behaviour, so every method is
 * {@link Disabled} until the matching finding is resolved. Workflow:
 *
 * <ol>
 *   <li>Land the fix for finding #N (+ its contract-card / manifest sync).</li>
 *   <li>Delete the {@code @Disabled} on the matching test here, in the SAME commit.</li>
 *   <li>The suite must stay green — a re-enabled test that fails means the fix is incomplete.</li>
 * </ol>
 *
 * While every method is disabled the Spring context is never started, so this class is build-safe on
 * the current (un-hardened) codebase. When enabling tests, wire a test datastore (Testcontainers
 * Postgres, or H2 in an {@code application-test.properties}) and an "it"/"test" profile so the full
 * context boots without the dev MinIO/SMTP services.
 *
 * Requests use raw JSON rather than DTO builders to stay decoupled from DTO field churn.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    // ── T3 / finding #3 — payment endpoints require auth + server-side amount binding ──────────────
    @Test
    @Disabled("Enable when #3 (payment integrity) lands: /payments/** must require auth")
    void createOrder_isRejectedForAnonymousCaller() throws Exception {
        String body = """
                { "amount": 1, "currency": "INR", "referenceId": "order_1" }
                """;
        mockMvc.perform(post("/api/v1/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── T4 / finding #4 — webhook signature verification fails CLOSED ──────────────────────────────
    @Test
    @Disabled("Enable when #4 (fail-closed webhook) lands")
    void webhook_withForgedOrMissingSignature_isRejected() throws Exception {
        String forged = """
                { "event": "payment.captured",
                  "payload": { "payment": { "entity": { "order_id": "order_1", "id": "pay_x" } } } }
                """;
        // No / wrong signature header must NOT capture the payment.
        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "not-a-valid-signature")
                        .content(forged))
                .andExpect(status().isBadRequest());
    }

    // ── T7 / finding #7 — password-reset token is short-lived and single-use ───────────────────────
    @Test
    @Disabled("Enable when #7 (short single-use challenge tokens) lands")
    void resetToken_cannotBeReplayed() throws Exception {
        // Precondition (set up in the enabled version): obtain a valid reset token for a seeded user.
        String token = "<obtain-via-forgot-password-in-enabled-version>";
        String body = """
                { "token": "%s", "newPassword": "NewPassw0rd!" }
                """.formatted(token);

        // First use succeeds...
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        // ...second use of the same token is rejected (single-use).
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── T8 / finding #8 — a JWT is invalidated by a password change (token-version/passwordChangedAt) ─
    @Test
    @Disabled("Enable when #8 (token revocation) lands")
    void jwt_isRejectedAfterPasswordChange() throws Exception {
        // Enabled version: log in -> capture token -> change password -> reuse old token.
        String staleToken = "<captured-before-password-change>";
        mockMvc.perform(get("/api/v1/orders/mine")
                        .header("Authorization", "Bearer " + staleToken))
                .andExpect(status().isUnauthorized());
    }

    // ── T9 / finding #9 — weak / common passwords are rejected at register ─────────────────────────
    @Test
    @Disabled("Enable when #9 (stronger password policy) lands")
    void register_rejectsWeakAndBreachedPasswords() throws Exception {
        // "password1" satisfies today's 8-char + letter + digit rule but is a known common password.
        String body = """
                { "firstName": "T", "lastName": "U", "email": "weak@example.com",
                  "phone": "+911111111111", "password": "password1" }
                """;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── TA / finding A — CORS is a strict allowlist, never "*" ─────────────────────────────────────
    @Test
    @Disabled("Enable when finding A (CORS allowlist) lands; set app.cors.allowed-origins in test props")
    void cors_disallowedOrigin_isNotEchoed() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Origin", "https://evil.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @Disabled("Enable when finding A (CORS allowlist) lands; set app.cors.allowed-origins in test props")
    void cors_neverUsesWildcard() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Origin", "https://allowed.cloudfront.net"))
                .andExpect(header().string("Access-Control-Allow-Origin",
                        org.hamcrest.Matchers.not("*")));
    }
}
