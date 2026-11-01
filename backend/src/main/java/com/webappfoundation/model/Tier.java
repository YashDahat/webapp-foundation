package com.webappfoundation.model;

/**
 * Loyalty tier, set by the nightly {@code score} recalculation. Tiers mostly improve non-cash perks;
 * total cash value returned stays ~1–2% of spend. Ordinal order is the tier ranking (Bronze lowest).
 */
public enum Tier {
    BRONZE,
    SILVER,
    GOLD,
    PLATINUM
}
