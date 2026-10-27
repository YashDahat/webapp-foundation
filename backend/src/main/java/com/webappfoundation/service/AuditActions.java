package com.webappfoundation.service;

/**
 * Well-known audit action names used by the foundation. Generated projects may pass their own action
 * strings to {@link AuditService} — these are just the shared constants for the fenced flows.
 */
public final class AuditActions {
    private AuditActions() {}

    // Auth
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String REGISTER = "REGISTER";
    public static final String SIGNUP_VERIFIED = "SIGNUP_VERIFIED";
    public static final String TWO_FACTOR_VERIFIED = "TWO_FACTOR_VERIFIED";
    public static final String TWO_FACTOR_ENABLED = "TWO_FACTOR_ENABLED";
    public static final String TWO_FACTOR_DISABLED = "TWO_FACTOR_DISABLED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";

    // Admin user management
    public static final String USER_ROLE_CHANGED = "USER_ROLE_CHANGED";
    public static final String USER_DELETED = "USER_DELETED";
    public static final String USER_BULK_IMPORT = "USER_BULK_IMPORT";

    // Catalog / products
    public static final String PRODUCT_CREATED = "PRODUCT_CREATED";
    public static final String PRODUCT_UPDATED = "PRODUCT_UPDATED";
    public static final String PRODUCT_DELETED = "PRODUCT_DELETED";
    public static final String PRODUCT_BULK_IMPORT = "PRODUCT_BULK_IMPORT";

    // Events
    public static final String EVENT_CREATED = "EVENT_CREATED";
    public static final String EVENT_UPDATED = "EVENT_UPDATED";
    public static final String EVENT_DELETED = "EVENT_DELETED";
    public static final String EVENT_BULK_IMPORT = "EVENT_BULK_IMPORT";

    // Recommendations
    public static final String RECOMMENDATION_CREATED = "RECOMMENDATION_CREATED";
    public static final String RECOMMENDATION_UPDATED = "RECOMMENDATION_UPDATED";
    public static final String RECOMMENDATION_DELETED = "RECOMMENDATION_DELETED";
    public static final String RECOMMENDATION_BULK_IMPORT = "RECOMMENDATION_BULK_IMPORT";

    // Orders
    public static final String ORDER_PLACED = "ORDER_PLACED";
    public static final String ORDER_PAID = "ORDER_PAID";
    public static final String ORDER_STAGE_ADVANCED = "ORDER_STAGE_ADVANCED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String ORDER_MARK_PAID = "ORDER_MARK_PAID";
    public static final String ORDER_RESERVATION_EXPIRED = "ORDER_RESERVATION_EXPIRED";
    public static final String ORDER_BULK_IMPORT = "ORDER_BULK_IMPORT";

    // Order board
    public static final String ORDER_BLOCKED = "ORDER_BLOCKED";
    public static final String ORDER_RESUMED = "ORDER_RESUMED";
    public static final String ORDER_ASSIGNED = "ORDER_ASSIGNED";
    public static final String ORDER_PAYMENT_LINK_SENT = "ORDER_PAYMENT_LINK_SENT";
    public static final String ORDER_REFUNDED = "ORDER_REFUNDED";
    public static final String SPRINT_COMPLETED = "SPRINT_COMPLETED";
    public static final String ORDER_CARRIED_FORWARD = "ORDER_CARRIED_FORWARD";
    public static final String WORKFLOW_STAGE_MIGRATED = "WORKFLOW_STAGE_MIGRATED";

    // Workflow / connectors admin
    public static final String WORKFLOW_UPDATED = "WORKFLOW_UPDATED";
    public static final String CONNECTOR_UPDATED = "CONNECTOR_UPDATED";

    // Branches
    public static final String BRANCH_CREATED = "BRANCH_CREATED";
    public static final String BRANCH_UPDATED = "BRANCH_UPDATED";
    public static final String BRANCH_DELETED = "BRANCH_DELETED";
    public static final String BRANCH_BULK_IMPORT = "BRANCH_BULK_IMPORT";

    // User address book
    public static final String ADDRESS_ADDED = "ADDRESS_ADDED";
    public static final String ADDRESS_UPDATED = "ADDRESS_UPDATED";
    public static final String ADDRESS_DELETED = "ADDRESS_DELETED";
    public static final String ADDRESS_DEFAULT_SET = "ADDRESS_DEFAULT_SET";

    // Post-checkout feedback
    public static final String FEEDBACK_SUBMITTED = "FEEDBACK_SUBMITTED";

    // Product reviews
    public static final String REVIEW_SUBMITTED = "REVIEW_SUBMITTED";
    public static final String REVIEW_DELETED = "REVIEW_DELETED";

    // Outcomes
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILURE = "FAILURE";
}
