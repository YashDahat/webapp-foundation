package com.webappfoundation.model;

/** Status of one {@link WorkflowExecution} (a single connector-action invocation for an order+stage+binding). */
public enum ExecutionStatus {
    PENDING,
    SUCCESS,
    FAILED
}
