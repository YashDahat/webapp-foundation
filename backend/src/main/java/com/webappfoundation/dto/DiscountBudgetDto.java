package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Admin view of a monthly {@link com.webappfoundation.model.DiscountBudget} row. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountBudgetDto {
    private Long id;
    private String month;
    private BigDecimal budgetAmount;
    private BigDecimal spentToDate;
}
