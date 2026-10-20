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

    // Outcomes
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILURE = "FAILURE";
}
