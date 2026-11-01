package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The loyalty state for one customer, kept 1:1 with {@code User} (PK = userId) so the fenced auth
 * {@code User} entity is never widened. Holds the engagement Loyalty Score + tier, the denormalised
 * spendable coin balance (= Σ confirmed, unexpired coin-ledger entries), the stats the discount engine
 * reads (AOV, order gap, return rate), the fixed holdout flag, and DPDP tracking consent.
 *
 * <p>The Loyalty Score measures engagement and is NEVER spent; coins are the redeemable currency.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "loyalty_profile")
public class LoyaltyProfile {

    /** The owning user's id ({@code User.id}); this is the primary key, not a generated value. */
    @Id
    private Integer userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Tier tier = Tier.BRONZE;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal score = BigDecimal.ZERO;

    private Instant scoreUpdatedAt;

    /** Denormalised spendable balance; the coin ledger is the source of truth. */
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal coinBalance = BigDecimal.ZERO;

    /** Rolling average paid order value — the basis for a friendly min-spend on percentage offers. */
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal avgOrderValue = BigDecimal.ZERO;

    /** The customer's usual gap between orders (days) — drives win-back timing + churn risk. */
    private Integer usualOrderGapDays;

    private Instant lastOrderAt;

    /** Fraction 0..1 of orders returned over the trailing window — guards block codes above the threshold. */
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal returnRate = BigDecimal.ZERO;

    /** Fixed random ~10% of customers never receive triggered codes (they still get tier perks). */
    @Column(nullable = false)
    @Builder.Default
    private boolean inHoldout = false;

    private LocalDate birthday;

    private Instant profileCompletedAt;

    /** A pending downgrade: tier drops only after a 30-day grace window (gives the customer a chance to stay). */
    @Enumerated(EnumType.STRING)
    private Tier pendingDowngradeTier;

    private Instant downgradeEffectiveAt;

    /** When the customer consented (DPDP) to behavioural tracking + personalised offers; null = no consent. */
    private Instant trackingConsentAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
