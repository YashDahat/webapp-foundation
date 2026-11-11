package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Admin-editable scoring rule for one {@link LoyaltyEventType}: points, a per-day cap, whether it can
 * only happen once ever, and whether it also earns coins. Seeded from the design spec's scoring table.
 * {@code PURCHASE} is special-cased in code (points scale with amount paid; no per-day cap).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "loyalty_event_rule")
public class LoyaltyEventRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private LoyaltyEventType eventType;

    /** Points per occurrence (for PURCHASE this is points per ₹1,000 paid). */
    @Column(nullable = false)
    private BigDecimal points;

    /** Max times per day this event earns points; null = no per-day cap. */
    private Integer perDayCap;

    @Column(nullable = false)
    @Builder.Default
    private boolean onceOnly = false;

    /**
     * When true, a non-PURCHASE occurrence also credits spendable reward coins equal to the points awarded
     * (rounded down), immediately CONFIRMED. PURCHASE coins come from {@code CoinService.earnForOrder} instead.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean earnsCoins = false;
}
