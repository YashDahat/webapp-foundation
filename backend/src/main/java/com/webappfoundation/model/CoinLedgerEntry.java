package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An append-only reward-coin ledger entry (signed: + earned, − redeemed/expired). Coins are kept as a
 * ledger rather than a single balance field so every coin can be traced and reversed on a return. The
 * spendable balance = Σ {@code CONFIRMED} entries past the earn hold and before {@code expiresAt}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "coin_ledger_entry", indexes = {
        @Index(name = "idx_coin_ledger_user", columnList = "userId"),
        @Index(name = "idx_coin_ledger_order", columnList = "orderId")
})
public class CoinLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer userId;

    /** Signed coin delta: positive when earned, negative when redeemed/expired/reversed-out. */
    @Column(nullable = false)
    private BigDecimal coins;

    /** Why the entry exists (e.g. {@code purchase}, {@code review}, {@code referral}, {@code redeem}, {@code expiry}). */
    @Column(nullable = false)
    private String reason;

    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CoinStatus status;

    @Column(nullable = false)
    private Instant earnedAt;

    /** 12 months after earning by default; past this the entry is expired by the nightly job. */
    private Instant expiresAt;
}
