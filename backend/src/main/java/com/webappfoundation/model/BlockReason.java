package com.webappfoundation.model;

/**
 * Why an order is blocked on the board. Orthogonal to the workflow stage — a blocked order keeps its
 * stage but pauses its time-in-stage timer until resumed. Set via {@code OrderService.block(...)}.
 */
public enum BlockReason {
    OUT_OF_STOCK,
    CUSTOMER_UNREACHABLE,
    PAYMENT_ISSUE,
    STAFF_UNAVAILABLE,
    ADDRESS_ISSUE,
    OTHER
}
