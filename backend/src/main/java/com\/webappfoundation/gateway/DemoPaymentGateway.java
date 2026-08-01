package com.webappfoundation.gateway;

import java.util.Optional;
import java.util.UUID;

/**
 * No-credentials fallback used when no Razorpay keys are configured. It synthesizes order ids
 * and accepts verification so the generated app boots and the checkout flow works end to end
 * in a demo without real gateway keys. There are no real webhooks in demo mode.
 */
public class DemoPaymentGateway implements PaymentGateway {

    @Override
    public String createOrder(int amountMinor, String currency, String receipt) {
        return "demo_order_" + UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public String publicKey() {
        return "demo_key";
    }

    @Override
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        return true;
    }

    @Override
    public boolean verifyWebhook(String payload, String signature) {
        return true;
    }

    @Override
    public Optional<GatewayWebhookEvent> parseWebhook(String payload) {
        return Optional.empty();
    }
}
