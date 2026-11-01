package com.webappfoundation.dto;

import com.webappfoundation.model.OfferType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Admin create payload for a manual or public campaign code. {@code userId} set = a personal code;
 * null = a public campaign code (give it a {@code campaignId} + a {@code maxUses} cap). Manual codes are
 * recorded with the approving staff id and audited.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOfferRequest {
    private String code;
    private Integer userId;
    private OfferType type;
    private BigDecimal value;
    private BigDecimal minSpend;
    private BigDecimal maxDiscount;
    private Integer maxUses;
    private Instant startsAt;
    private Instant expiresAt;
    private String campaignId;
}
