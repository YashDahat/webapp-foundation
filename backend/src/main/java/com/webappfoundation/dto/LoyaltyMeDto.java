package com.webappfoundation.dto;

import com.webappfoundation.model.Tier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * The signed-in customer's own loyalty snapshot for the account page: tier + score, coin balance, and a
 * progress-to-next-tier nudge. {@code trackingConsent} gates behavioural activity beacons (DPDP);
 * {@code discountsEnabled} mirrors the programme master switch so the UI can hide discount affordances.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyMeDto {
    private Integer userId;
    private Tier tier;
    private String tierName;
    private BigDecimal score;
    private BigDecimal coinBalance;
    private Tier nextTier;              // null at the top tier
    private Integer pointsToNextTier;   // 0 at the top tier
    private boolean nearNextTier;       // within 10% of the next threshold → show the nudge
    private boolean inHoldout;
    private boolean trackingConsent;
    private boolean discountsEnabled;
}
