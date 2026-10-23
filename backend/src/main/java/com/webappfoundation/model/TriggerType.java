package com.webappfoundation.model;

/**
 * What caused a stage transition. Automatic triggers ({@code SYSTEM}, {@code PAYMENT}, {@code WEBHOOK})
 * are idempotent (no-op if the order is already at/past the target stage); {@code ADMIN} may deliberately
 * re-enter a stage; {@code RETRY} re-runs a previously failed/succeeded binding.
 */
public enum TriggerType {
    SYSTEM,
    PAYMENT,
    WEBHOOK,
    ADMIN,
    RETRY
}
