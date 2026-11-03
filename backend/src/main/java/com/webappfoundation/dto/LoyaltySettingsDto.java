package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Read + update shape for the global loyalty settings singleton. {@code discountsEnabled} is the
 * client-controllable master kill switch for the discount/promotion side.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltySettingsDto {
    private boolean discountsEnabled;
    private BigDecimal coinValue;
    private Integer maxCoinPctOfOrder;
    private BigDecimal minOrderForCoins;
    private Integer coinExpiryMonths;
    private Integer scoreHalfLifeDays;
    private Integer dailyCap;
    private Integer monthlyCap;
    private Integer holdoutPct;
    private Integer discountPctTarget;
    private Integer returnRateBlockPct;
    private Integer newCodeCooldownDays;
    private Integer returnWindowDays;
    private Integer marginFloorPct;
}
