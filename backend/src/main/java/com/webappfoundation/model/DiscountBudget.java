package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * The monthly discount budget (= discount% target × the month's sales target) and how much has been
 * spent so far. Pacing shrinks offers when spend runs ahead of plan; once {@code spentToDate} reaches
 * {@code budgetAmount}, only non-cash rewards + tier perks are issued until next month.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "discount_budget")
public class DiscountBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Budget month in {@code yyyy-MM} form (one row per month). Column renamed off the SQL reserved word "month". */
    @Column(name = "budget_month", nullable = false, unique = true)
    private String month;

    @Column(nullable = false)
    private BigDecimal budgetAmount;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal spentToDate = BigDecimal.ZERO;
}
