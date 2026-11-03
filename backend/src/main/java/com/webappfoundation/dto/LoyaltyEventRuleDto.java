package com.webappfoundation.dto;

import com.webappfoundation.model.LoyaltyEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Admin view of a {@link com.webappfoundation.model.LoyaltyEventRule} scoring row. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyEventRuleDto {
    private Long id;
    private LoyaltyEventType eventType;
    private BigDecimal points;
    private Integer perDayCap;
    private boolean onceOnly;
    private boolean earnsCoins;
}
