package com.webappfoundation.dto;

import com.webappfoundation.model.OfferTrigger;
import com.webappfoundation.model.Tier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregates for the admin loyalty metrics dashboard — the lines that feed the growth tracker
 * (Discount %, Repeat %, Cost per New Customer). Judged against the holdout, not raw redemptions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyMetricsDto {
    private long members;           // customers with a loyalty profile, not in holdout
    private long holdout;           // customers in the holdout group
    private BudgetMetrics budget;
    private BigDecimal coinLiability;   // Σ confirmed coin balances × coin value (₹)
    private List<TierCount> tierMix;
    private List<TriggerStat> redemptionByTrigger;
    private HoldoutComparison holdoutComparison;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BudgetMetrics {
        private String month;
        private BigDecimal budgetAmount;
        private BigDecimal spentToDate;
        private BigDecimal grossSales;          // this month
        private BigDecimal discountPctOfGross;  // spend / gross × 100
        private int pctOfBudgetUsed;
        private int targetPct;                  // the configured discount % target
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TierCount {
        private Tier tier;
        private long count;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TriggerStat {
        private OfferTrigger trigger;
        private long issued;
        private long redeemed;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class HoldoutComparison {
        private BigDecimal memberRepeatRate;        // fraction 0..1
        private BigDecimal holdoutRepeatRate;
        private BigDecimal memberAvgRevenue;        // ₹ per customer
        private BigDecimal holdoutAvgRevenue;
        private BigDecimal incrementalRevenuePerCustomer;   // member − holdout
    }
}
