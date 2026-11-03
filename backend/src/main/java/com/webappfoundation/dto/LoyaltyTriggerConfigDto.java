package com.webappfoundation.dto;

import com.webappfoundation.model.OfferTrigger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Admin view of a {@link com.webappfoundation.model.LoyaltyTriggerConfig} row. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyTriggerConfigDto {
    private Long id;
    private OfferTrigger trigger;
    private boolean enabled;
    private String rungsJson;
    private Integer expiryDays;
}
