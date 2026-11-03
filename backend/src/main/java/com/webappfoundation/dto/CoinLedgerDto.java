package com.webappfoundation.dto;

import com.webappfoundation.model.CoinStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** A single reward-coin ledger line for the customer's coin history. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoinLedgerDto {
    private Long id;
    private BigDecimal coins;       // signed: + earned, − redeemed/expired
    private String reason;
    private CoinStatus status;
    private Instant earnedAt;
    private Instant expiresAt;
}
