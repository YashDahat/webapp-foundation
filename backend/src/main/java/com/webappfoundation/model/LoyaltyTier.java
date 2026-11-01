package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Admin-editable configuration for one {@link Tier}: the score threshold to reach it, the coins earned
 * per ₹100 paid, and a human-readable perks blurb. Seeded with the four default tiers from the design
 * spec; admins tune thresholds at {@code /admin/loyalty/tiers} so the tier mix matches their base.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "loyalty_tier")
public class LoyaltyTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private Tier tier;

    @Column(nullable = false)
    private String name;

    /** Minimum Loyalty Score to hold this tier (Bronze = 0). */
    @Column(nullable = false)
    private Integer minScore;

    /** Coins earned per ₹100 paid at this tier (Bronze 2 → Platinum 4). */
    @Column(nullable = false)
    private BigDecimal coinsPer100;

    @Column(columnDefinition = "text")
    private String perks;
}
