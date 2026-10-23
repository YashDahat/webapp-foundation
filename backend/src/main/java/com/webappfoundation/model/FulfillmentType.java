package com.webappfoundation.model;

/** How an order is fulfilled. {@code SELF_PICKUP} may be paid at the counter; {@code DELIVERY} needs an address. */
public enum FulfillmentType {
    DELIVERY,
    SELF_PICKUP
}
