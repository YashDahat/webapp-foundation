package com.webappfoundation.model;

/**
 * Lifecycle of a coin-ledger entry. Earned coins start {@code PENDING} until the return window closes,
 * then {@code CONFIRMED} (spendable); a return reverses them, and unredeemed coins {@code EXPIRED} after
 * the configured lifetime. The spendable balance = Σ {@code CONFIRMED} entries not yet expired.
 */
public enum CoinStatus {
    PENDING,
    CONFIRMED,
    REVERSED,
    EXPIRED
}
