package com.webappfoundation.service;

import com.webappfoundation.model.DiscountBudget;
import com.webappfoundation.repository.DiscountBudgetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Monthly discount-budget pacing. If discounts run ahead of plan the pace factor shrinks (so offers get
 * smaller); once the month's budget is fully used, only non-cash rewards are issued. When no budget row
 * exists for the month the budget is treated as unlimited (pace 1.0, never exhausted) and spend is untracked.
 *
 * <p>{@code P = clamp(planned-to-date / actual, 0.5, 1.2)}, where planned-to-date = budget × fraction of the
 * month elapsed.
 */
@Service
public class BudgetPacingService {

    private static final BigDecimal MIN_PACE = new BigDecimal("0.5");
    private static final BigDecimal MAX_PACE = new BigDecimal("1.2");

    private final DiscountBudgetRepository budgetRepository;

    public BudgetPacingService(DiscountBudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    /** The current-month pace factor in [0.5, 1.2]; 1.0 when no budget is configured. */
    public BigDecimal paceFactor() {
        DiscountBudget b = currentBudget();
        if (b == null || b.getBudgetAmount().signum() <= 0) {
            return BigDecimal.ONE;
        }
        LocalDate today = LocalDate.now();
        BigDecimal fractionPassed = BigDecimal.valueOf(today.getDayOfMonth())
                .divide(BigDecimal.valueOf(today.lengthOfMonth()), 4, RoundingMode.HALF_UP);
        BigDecimal planned = b.getBudgetAmount().multiply(fractionPassed);
        BigDecimal actual = b.getSpentToDate().max(BigDecimal.ONE);
        BigDecimal pace = planned.divide(actual, 4, RoundingMode.HALF_UP);
        return pace.max(MIN_PACE).min(MAX_PACE);
    }

    /** True when the current month's budget is configured and fully spent → cash offers pause. */
    public boolean budgetExhausted() {
        DiscountBudget b = currentBudget();
        return b != null && b.getBudgetAmount().signum() > 0 && b.getSpentToDate().compareTo(b.getBudgetAmount()) >= 0;
    }

    /** Record discount spend against the current month (no-op when no budget row exists for it). */
    @Transactional
    public void recordSpend(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return;
        budgetRepository.addSpend(currentMonth(), amount);
    }

    private DiscountBudget currentBudget() {
        return budgetRepository.findByMonth(currentMonth()).orElse(null);
    }

    private String currentMonth() {
        return YearMonth.now().toString();
    }
}
