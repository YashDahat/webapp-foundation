package com.webappfoundation.model;

/** How an order is paid. {@code PAY_AT_COUNTER} is only valid with {@code SELF_PICKUP} (placed unpaid). */
public enum PaymentMode {
    ONLINE,
    PAY_AT_COUNTER
}
