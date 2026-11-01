package com.webappfoundation.dto;

import com.webappfoundation.model.Tier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Admin view of a {@link com.webappfoundation.model.LoyaltyTier} config row. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyTierDto {
    private Long id;
    private Tier tier;
    private String name;
    private Integer minScore;
    private BigDecimal coinsPer100;
    private String perks;
}
