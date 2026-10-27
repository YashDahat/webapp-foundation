package com.webappfoundation.security;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Automated checks for the Security Hardening Plan (see {@code /security_hardening_plan.md} — "Security
 * test matrix"). Runs under the {@code test} profile (in-memory H2, see
 * {@code src/test/resources/application-test.properties}) so the full Spring context boots without
 * Postgres/Docker.
 *
 * <p>Enabled rows assert the current (post-fix) behaviour. Rows whose fix has not landed yet stay
 * {@link Disabled} with a reason naming the finding; delete the annotation in the same commit as the
 * fix (a re-enabled test that fails means the fix is incomplete).
 *
 * <p>Requests use raw JSON rather than DTO builders to stay decoupled from DTO field churn.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    // ── T3 / finding #3 — payment endpoints require auth (server-side amount binding also covered by
    //    OrderService.onPaymentCaptured's amount check; exercised in the order-flow tests) ──────────
    @Test
    void createOrder_isRejectedForAnonymousCaller() throws Exception {
        String body = """
                { "amount": 1, "currency": "INR", "referenceId": "order_1" }
                """;
        mockMvc.perform(post("/api/v1/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                // Anonymous access to an authenticated() endpoint is denied (401 or 403), never 200.
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    assertThat(s).as("anonymous create-order must be denied").isIn(401, 403);
                });
    }

    // ── TA / finding A — CORS is a strict allowlist, never "*" ─────────────────────────────────────
    @Test
    void cors_disallowedOrigin_isNotEchoed() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Origin", "https://evil.example"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void cors_allowedOrigin_isEchoedAndNeverWildcard() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Origin", "https://allowed.cloudfront.net"))
                .andExpect(header().string("Access-Control-Allow-Origin", "https://allowed.cloudfront.net"));
    }

    // ── T7 / finding #7 — password-reset token is short-lived and single-use ───────────────────────
    @Test
    @Disabled("Enable when #7 (short single-use challenge tokens) lands")
    void resetToken_cannotBeReplayed() throws Exception {
        String token = "<obtain-via-forgot-password-in-enabled-version>";
        String body = """
                { "token": "%s", "newPassword": "NewPassw0rd!" }
                """.formatted(token);
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── T8 / finding #8 — a JWT is invalidated by a password change ────────────────────────────────
    @Test
    @Disabled("Enable when #8 (token revocation) lands")
    void jwt_isRejectedAfterPasswordChange() throws Exception {
        String staleToken = "<captured-before-password-change>";
        mockMvc.perform(get("/api/v1/orders/mine")
                        .header("Authorization", "Bearer " + staleToken))
                .andExpect(status().isUnauthorized());
    }

    // ── T9 / finding #9 — weak / common passwords are rejected at register ─────────────────────────
    @Test
    @Disabled("Enable when #9 (stronger password policy) lands")
    void register_rejectsWeakAndBreachedPasswords() throws Exception {
        String body = """
                { "firstName": "T", "lastName": "U", "email": "weak@example.com",
                  "phone": "+911111111111", "password": "password1" }
                """;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
