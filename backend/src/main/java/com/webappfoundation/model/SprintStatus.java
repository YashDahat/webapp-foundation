package com.webappfoundation.model;

/**
 * Lifecycle of an {@link OrderSprint}. Exactly one {@code ACTIVE} sprint exists per workflow at any time
 * (invariant enforced by {@code SprintService}); completing it closes it ({@code CLOSED}) and opens the next.
 */
public enum SprintStatus {
    ACTIVE,
    CLOSED
}
