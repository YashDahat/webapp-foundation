package com.webappfoundation.model;

/**
 * A tracked customer action that contributes to the Loyalty Score (and sometimes earns coins). Point
 * values, per-day caps, once-only and earns-coins flags are configured per type in {@code LoyaltyEventRule}
 * (seeded from the design spec), not hardcoded here.
 */
public enum LoyaltyEventType {
    SIGNUP,
    PROFILE_COMPLETE,
    DAILY_VISIT,
    PRODUCT_VIEW,
    WISHLIST,
    ADD_TO_CART,
    PURCHASE,
    REVIEW,
    REVIEW_MEDIA,
    SHARE_CLICK,
    REFERRAL_SIGNUP,
    REFERRAL_PURCHASE,
    NOTIF_OPT_IN
}
