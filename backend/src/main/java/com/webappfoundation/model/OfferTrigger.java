package com.webappfoundation.model;

/**
 * The moment that caused an {@code Offer} to be issued. Offers come from triggers, not the calendar.
 * {@code MANUAL} is a staff-created code (needs manager approval); {@code FESTIVE} backs planned public
 * campaign codes. Each trigger's rungs, base offer and expiry are configured in {@code LoyaltyTriggerConfig}.
 */
public enum OfferTrigger {
    WELCOME,
    ABANDONED_CART,
    WIN_BACK,
    TIER_UP,
    MILESTONE,
    BIRTHDAY,
    SLOW_STOCK,
    FESTIVE,
    SURPRISE,
    MANUAL
}
