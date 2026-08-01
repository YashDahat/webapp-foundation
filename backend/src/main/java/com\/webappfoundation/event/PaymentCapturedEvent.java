package com.webappfoundation.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

/**
 * Published by PaymentService when a payment is verified/captured. A business service that
 * needs to react asynchronously (e.g. mark an order paid on a webhook) listens with an
 * {@code @EventListener} — this keeps the dependency direction payment → consumer and never
 * the reverse, so no bean cycle can form.
 */
@Getter
public class PaymentCapturedEvent extends ApplicationEvent {

    private final String referenceId;
    private final String gatewayPaymentId;
    private final BigDecimal amount;

    public PaymentCapturedEvent(Object source, String referenceId, String gatewayPaymentId, BigDecimal amount) {
        super(source);
        this.referenceId = referenceId;
        this.gatewayPaymentId = gatewayPaymentId;
        this.amount = amount;
    }
}
