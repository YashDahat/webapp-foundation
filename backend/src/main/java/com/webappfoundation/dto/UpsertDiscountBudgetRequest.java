package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Create/update payload for a monthly discount budget ({@code month} = yyyy-MM). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertDiscountBudgetRequest {
    private String month;
    private BigDecimal budgetAmount;
}
