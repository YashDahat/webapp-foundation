package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin-editable configuration for one {@link OfferTrigger}: whether it is enabled, its offer-ladder
 * rungs (JSON), and how long an issued code lives. Seeded from the design spec's trigger table. The
 * engine reads these when deciding an offer; disabling a trigger (or the global {@code discountsEnabled}
 * kill switch in {@code LoyaltySettings}) stops it issuing codes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "loyalty_trigger_config")
public class LoyaltyTriggerConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private OfferTrigger trigger;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * The offer ladder (rung 1 → N), cheapest first, as a JSON array — e.g.
     * {@code [{"type":"FREE_SHIP"},{"type":"PCT","value":10,"maxDiscount":300}]}.
     */
    @Column(columnDefinition = "text")
    private String rungsJson;

    /** How many days an issued code stays valid. */
    @Column(nullable = false)
    private Integer expiryDays;
}
