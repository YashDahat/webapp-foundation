package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One scored customer action. The nightly job sums {@code points} with a 90-day half-life decay to get
 * the Loyalty Score. Purchase events are {@code PENDING} until the return window closes; non-purchase
 * events are {@code CONFIRMED} on write. This is a behavioural stream, deliberately separate from the
 * security {@code audit_log}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "loyalty_event", indexes = {
        @Index(name = "idx_loyalty_event_user", columnList = "userId"),
        @Index(name = "idx_loyalty_event_user_type_created", columnList = "userId,type,createdAt")
})
public class LoyaltyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoyaltyEventType type;

    @Column(nullable = false)
    private BigDecimal points;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoyaltyEventStatus status;

    /** The related order, when the event is purchase/return driven; null otherwise. */
    private Long orderId;

    /** Small JSON blob of context (e.g. itemType/itemId, referralUserId) — never secrets/PII. */
    @Column(columnDefinition = "text")
    private String metaJson;

    @Column(nullable = false)
    private Instant createdAt;

    /** When a PENDING purchase event becomes CONFIRMED (return window end). */
    private Instant confirmAt;
}
