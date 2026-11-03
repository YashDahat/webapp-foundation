package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An immutable record that a code was applied to an order. Written in the same transaction as the order
 * (and the atomic {@code usedCount} increment) so a code cannot be redeemed twice in parallel checkouts.
 * {@code grossMarginAfter} is kept for the "never below the margin floor" audit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "loyalty_redemption", indexes = {
        @Index(name = "idx_redemption_order", columnList = "orderId"),
        @Index(name = "idx_redemption_user", columnList = "userId")
})
public class Redemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private Long orderId;

    private Integer userId;

    /** The absolute cash value taken off the order (positive rupees). */
    @Column(nullable = false)
    private BigDecimal discountValue;

    private BigDecimal grossMarginAfter;

    @Column(nullable = false)
    private Instant createdAt;
}
