package com.webappfoundation.dto;

import com.webappfoundation.model.OfferTrigger;
import com.webappfoundation.model.OfferType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Admin edit payload for an existing discount code. The code's identity ({@code code}) and scope
 * ({@code userId}) are immutable after creation, so they are not editable here — only the campaign
 * parameters. {@code trigger} is restricted to the admin-creatable categories (see {@code OfferAdminService}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOfferRequest {
    private OfferTrigger trigger;
    private OfferType type;
    private BigDecimal value;
    private BigDecimal minSpend;
    private BigDecimal maxDiscount;
    private Integer maxUses;
    private Instant startsAt;
    private Instant expiresAt;
    private String campaignId;
}
