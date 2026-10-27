package com.webappfoundation.security;

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
 * Endpoint-level checks for the Security Hardening Plan ("Security test matrix"). Runs under the
 * {@code test} profile (in-memory H2, see {@code src/test/resources/application-test.properties}).
 * Token-revocation (T7/T8) and login throttling (T6) live in {@link AuthHardeningTest}; webhook
 * fail-closed (T4) in {@link RazorpayWebhookFailClosedTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHardeningTest {

    @Autowired
    private MockMvc mockMvc;

    // ── T3 / finding #3 — payment endpoints require auth ───────────────────────────────────────────
    @Test
    void createOrder_isRejectedForAnonymousCaller() throws Exception {
        String body = """
                { "amount": 1, "currency": "INR", "referenceId": "order_1" }
                """;
        mockMvc.perform(post("/api/v1/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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

    // ── T9 / finding #9 — weak / common passwords are rejected at register ─────────────────────────
    @Test
    void register_rejectsWeakAndCommonPasswords() throws Exception {
        String body = """
                { "firstName": "T", "lastName": "U", "email": "weak@example.com",
                  "phone": "+911111111111", "password": "password1" }
                """;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
