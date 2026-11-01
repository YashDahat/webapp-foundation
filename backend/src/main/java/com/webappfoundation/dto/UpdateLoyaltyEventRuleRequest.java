package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Admin edit payload for an event scoring rule (the event type itself is fixed). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLoyaltyEventRuleRequest {
    private BigDecimal points;
    private Integer perDayCap;
    private Boolean onceOnly;
    private Boolean earnsCoins;
}
