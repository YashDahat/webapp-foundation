package com.webappfoundation.model;

/**
 * Minimal system payment state, kept separate from the business workflow (which is DB-configured stages).
 * {@code REFUND_PENDING} = a refund was computed and a {@code refund.*} action dispatched; {@code REFUNDED}
 * is set only when a refund connector confirms the money actually moved.
 */
public enum PaymentState {
    AWAITING_PAYMENT,
    PAID,
    FAILED,
    REFUND_PENDING,
    REFUNDED
}
