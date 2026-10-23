package com.webappfoundation.model;

/**
 * The relationship a {@link CatalogRecommendation} expresses. {@code UPSELL} = a better/pricier
 * alternative (shown as "Similar products"); {@code CROSS_SELL} = a complementary item ("Frequently bought
 * together"). {@code POST_PURCHASE} is intentionally deferred (the Razorpay spine has no off-session
 * charge), but the enum stays extensible.
 */
public enum RecommendationType {
    UPSELL,
    CROSS_SELL
}
