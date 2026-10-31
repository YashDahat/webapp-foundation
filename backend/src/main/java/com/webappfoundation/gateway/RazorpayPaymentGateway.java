package com.webappfoundation.gateway;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.webappfoundation.exception.PaymentGatewayException;
import org.json.JSONObject;

import java.util.Optional;

/**
 * Razorpay implementation of {@link PaymentGateway}. All Razorpay SDK coupling — and the
 * Razorpay webhook JSON shape — lives here and nowhere else; this is the only class that
 * imports com.razorpay.
 */
public class RazorpayPaymentGateway implements PaymentGateway {

    private final RazorpayClient client;
    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final boolean allowUnsigned;

    public RazorpayPaymentGateway(String keyId, String keySecret, String webhookSecret, boolean allowUnsigned) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
        this.allowUnsigned = allowUnsigned;
        try {
            this.client = new RazorpayClient(keyId, keySecret);
        } catch (RazorpayException e) {
            throw new PaymentGatewayException("Failed to initialize Razorpay client", e);
        }
    }

    @Override
    public String createOrder(int amountMinor, String currency, String receipt) {
        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountMinor);
            orderRequest.put("currency", currency);
            orderRequest.put("receipt", receipt);
            Order order = client.orders.create(orderRequest);
            return order.get("id");
        } catch (RazorpayException e) {
            throw new PaymentGatewayException("Failed to create Razorpay order", e);
        }
    }

    @Override
    public String publicKey() {
        return keyId;
    }

    @Override
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", orderId);
            options.put("razorpay_payment_id", paymentId);
            options.put("razorpay_signature", signature);
            return Utils.verifyPaymentSignature(options, keySecret);
        } catch (RazorpayException e) {
            return false;
        }
    }

    @Override
    public boolean verifyWebhook(String payload, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            // Fail CLOSED: with no secret we cannot verify, so reject — unless explicitly allowed
            // (dev only, razorpay.webhook.allow-unsigned=true). Never true in prod.
            return allowUnsigned;
        }
        try {
            return Utils.verifyWebhookSignature(payload, signature, webhookSecret);
        } catch (RazorpayException e) {
            throw new PaymentGatewayException("Webhook verification failed", e);
        }
    }

    @Override
    public Optional<GatewayWebhookEvent> parseWebhook(String payload) {
        try {
            JSONObject root = new JSONObject(payload);
            if (!"payment.captured".equals(root.optString("event"))) {
                return Optional.empty(); // only capture events reconcile a payment
            }
            JSONObject entity = root.getJSONObject("payload")
                    .getJSONObject("payment")
                    .getJSONObject("entity");
            String orderId = entity.optString("order_id", null);
            if (orderId == null) {
                return Optional.empty();
            }
            return Optional.of(new GatewayWebhookEvent(orderId, entity.optString("id", null), true));
        } catch (RuntimeException e) {
            return Optional.empty(); // malformed / unexpected payload — ignore rather than fail the callback
        }
    }
}
