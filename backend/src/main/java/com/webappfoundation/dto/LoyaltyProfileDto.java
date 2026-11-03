package com.webappfoundation.dto;

import com.webappfoundation.model.Tier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Admin customer-loyalty view (read-only grid + drill-in): one row per {@link com.webappfoundation.model.LoyaltyProfile}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyProfileDto {
    private Integer userId;
    private String userEmail;
    private String userName;
    private Tier tier;
    private BigDecimal score;
    private BigDecimal coinBalance;
    private BigDecimal avgOrderValue;
    private Integer usualOrderGapDays;
    private Instant lastOrderAt;
    private BigDecimal returnRate;
    private boolean inHoldout;
    private LocalDate birthday;
    private Instant scoreUpdatedAt;
}
