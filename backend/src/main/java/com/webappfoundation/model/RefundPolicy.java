package com.webappfoundation.model;

/** What refund a cancellation at a given {@link WorkflowStage} grants. PARTIAL uses {@code refundPercent}. */
public enum RefundPolicy {
    FULL,
    PARTIAL,
    NONE
}
