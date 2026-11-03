package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A discount code. Personal codes ({@code userId} set) are locked to one customer and single-use so a
 * leaked code is worthless to anyone else; public campaign codes ({@code userId} null) carry a total
 * {@code maxUses} cap and a short window. The redeemable value at checkout becomes a negative
 * {@code OrderAdjustment} via the discount connector — the order spine prices it, nothing new.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "loyalty_offer", indexes = {
        @Index(name = "idx_offer_user", columnList = "userId"),
        @Index(name = "idx_offer_campaign", columnList = "campaignId")
})
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Campaign prefix + random chars, look-alike-safe, e.g. {@code WB-7K4QXR} / {@code DIWALI10}. Unique. */
    @Column(nullable = false, unique = true)
    private String code;

    /** The customer this code is locked to; null for a public campaign code. */
    private Integer userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)   // "trigger" is a SQL reserved word
    private OfferTrigger trigger;

    /** Which rung of the trigger's offer ladder was issued (1 = cheapest). */
    private Integer rung;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfferType type;

    /** Percent (for PCT), rupees (FLAT), or coin count (COINS/GIFT); null for FREE_SHIP. */
    @Column(name = "offer_value")                      // "value" is a SQL reserved word
    private BigDecimal value;

    /** Minimum cart subtotal (after excluded items) for the code to apply. */
    private BigDecimal minSpend;

    /** Absolute cash ceiling for a percentage offer (e.g. ₹1,500). */
    private BigDecimal maxDiscount;

    @Column(nullable = false)
    @Builder.Default
    private Integer maxUses = 1;

    @Column(nullable = false)
    @Builder.Default
    private Integer usedCount = 0;

    private Instant startsAt;
    private Instant expiresAt;

    /** Groups public codes issued under one planned campaign (for the total usage cap + reporting). */
    private String campaignId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OfferStatus status = OfferStatus.ACTIVE;

    /** Staff id that approved a MANUAL code (manager approval + audit); null for engine-issued codes. */
    private Integer manualApprovedBy;

    @Column(nullable = false)
    private Instant createdAt;
}
