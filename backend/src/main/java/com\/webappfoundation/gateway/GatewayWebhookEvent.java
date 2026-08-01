package com.webappfoundation.gateway;

/**
 * Provider-neutral view of a payment webhook, produced by a {@link PaymentGateway} adapter and
 * consumed by PaymentService to reconcile a Payment. Keeps PaymentService free of any gateway's
 * webhook JSON shape.
 */
public record GatewayWebhookEvent(String gatewayOrderId, String gatewayPaymentId, boolean captured) {
}
