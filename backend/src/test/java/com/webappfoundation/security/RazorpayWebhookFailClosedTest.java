package com.webappfoundation.security;

import com.webappfoundation.gateway.RazorpayPaymentGateway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T4 / finding #4 — the Razorpay webhook verifier MUST fail CLOSED: with no webhook secret configured
 * it rejects by default, and only accepts an unsigned webhook when explicitly allowed (dev only).
 * A focused unit test (no Spring context) because the real {@code test} profile uses the demo gateway.
 */
class RazorpayWebhookFailClosedTest {

    private static final String PAYLOAD = """
            { "event": "payment.captured",
              "payload": { "payment": { "entity": { "order_id": "order_1", "id": "pay_x" } } } }
            """;

    @Test
    void noSecret_failsClosed_whenUnsignedNotAllowed() {
        RazorpayPaymentGateway gateway =
                new RazorpayPaymentGateway("rzp_test_key", "rzp_test_secret", "", false);
        assertThat(gateway.verifyWebhook(PAYLOAD, "any-signature"))
                .as("no webhook secret + allowUnsigned=false must reject")
                .isFalse();
    }

    @Test
    void noSecret_isAccepted_onlyWhenUnsignedExplicitlyAllowed() {
        RazorpayPaymentGateway gateway =
                new RazorpayPaymentGateway("rzp_test_key", "rzp_test_secret", "", true);
        assertThat(gateway.verifyWebhook(PAYLOAD, "any-signature"))
                .as("no webhook secret + allowUnsigned=true (dev) accepts")
                .isTrue();
    }

    @Test
    void withSecret_forgedSignature_isRejected() {
        RazorpayPaymentGateway gateway =
                new RazorpayPaymentGateway("rzp_test_key", "rzp_test_secret", "a-webhook-secret", false);
        assertThat(gateway.verifyWebhook(PAYLOAD, "deadbeef-not-a-valid-hmac"))
                .as("a forged signature against a configured secret must reject")
                .isFalse();
    }
}
