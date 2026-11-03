package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Admin edit payload for a tier config row (the four tiers are fixed; only their thresholds/perks tune). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLoyaltyTierRequest {
    private String name;
    private Integer minScore;
    private BigDecimal coinsPer100;
    private String perks;
}
