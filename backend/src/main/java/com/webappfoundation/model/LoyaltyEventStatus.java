package com.webappfoundation.model;

/**
 * Lifecycle of a {@code LoyaltyEvent}'s points. Purchase points start {@code PENDING} and only count
 * toward the score once the return window closes ({@code CONFIRMED}); a return/cancel moves them to
 * {@code REVERSED}. Non-purchase events are {@code CONFIRMED} immediately.
 */
public enum LoyaltyEventStatus {
    PENDING,
    CONFIRMED,
    REVERSED
}
