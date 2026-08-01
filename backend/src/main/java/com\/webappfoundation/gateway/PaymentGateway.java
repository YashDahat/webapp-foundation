package com.webappfoundation.gateway;

import java.util.Optional;

/**
 * The payment provider abstraction. PaymentService depends ONLY on this interface, so a new
 * provider (Stripe, Cashfree, a UPI PSP) is a new implementation plus one conditional bean in
 * PaymentGatewayConfig — never an edit to PaymentService, the controller, or any consumer.
 */
public interface PaymentGateway {

    /** Creates a gateway order for the given amount in minor units (e.g. paise); returns its id. */
    String createOrder(int amountMinor, String currency, String receipt);

    /** The public key the frontend checkout widget needs (may be a placeholder for gateways that need none). */
    String publicKey();

    /** Verifies the client-side payment signature returned by the checkout widget. */
    boolean verifySignature(String orderId, String paymentId, String signature);

    /** Verifies a server-to-server webhook signature. Returns true when there is nothing to verify against. */
    boolean verifyWebhook(String payload, String signature);

    /**
     * Parses a (signature-verified) webhook body into a provider-neutral event. Returns empty
     * when the payload is not a capture this gateway handles — all provider-specific JSON shape
     * lives in the adapter, never in PaymentService.
     */
    Optional<GatewayWebhookEvent> parseWebhook(String payload);
}
