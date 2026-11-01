package com.webappfoundation.dto;

import com.webappfoundation.model.OfferStatus;
import com.webappfoundation.model.OfferTrigger;
import com.webappfoundation.model.OfferType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** Admin view of an {@link com.webappfoundation.model.Offer} (discount code). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferDto {
    private Long id;
    private String code;
    private Integer userId;
    /** Resolved for the admin list (email of the locked-to customer); null for public codes. */
    private String userEmail;
    private OfferTrigger trigger;
    private Integer rung;
    private OfferType type;
    private BigDecimal value;
    private BigDecimal minSpend;
    private BigDecimal maxDiscount;
    private Integer maxUses;
    private Integer usedCount;
    private Instant startsAt;
    private Instant expiresAt;
    private String campaignId;
    private OfferStatus status;
    private Instant createdAt;
}
