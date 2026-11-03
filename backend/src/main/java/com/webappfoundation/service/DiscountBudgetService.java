package com.webappfoundation.service;

import com.webappfoundation.dto.DiscountBudgetDto;
import com.webappfoundation.dto.UpsertDiscountBudgetRequest;
import com.webappfoundation.model.DiscountBudget;
import com.webappfoundation.repository.DiscountBudgetRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Admin management of the monthly discount budget. The engine (Phase 3) reads the current month's row
 * for pacing; here admins set the budget amount per month. One row per {@code yyyy-MM}.
 */
@Service
public class DiscountBudgetService {

    private static final Pattern MONTH = Pattern.compile("\\d{4}-\\d{2}");

    private final DiscountBudgetRepository repository;
    private final AuditService auditService;

    public DiscountBudgetService(DiscountBudgetRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public List<DiscountBudgetDto> adminList() {
        return repository.findAllByOrderByMonthDesc().stream().map(this::toDto).toList();
    }

    @Transactional
    public DiscountBudgetDto upsert(UpsertDiscountBudgetRequest req) {
        if (req.getMonth() == null || !MONTH.matcher(req.getMonth()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "month must be in yyyy-MM form");
        }
        if (req.getBudgetAmount() == null || req.getBudgetAmount().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "budgetAmount must be >= 0");
        }
        DiscountBudget budget = repository.findByMonth(req.getMonth())
                .orElseGet(() -> DiscountBudget.builder().month(req.getMonth()).spentToDate(BigDecimal.ZERO).build());
        budget.setBudgetAmount(req.getBudgetAmount());
        DiscountBudget saved = repository.save(budget);
        auditService.record(AuditActions.LOYALTY_BUDGET_UPSERTED, AuditActions.SUCCESS, "DISCOUNT_BUDGET",
                String.valueOf(saved.getId()), "month=" + saved.getMonth() + " amount=" + saved.getBudgetAmount());
        return toDto(saved);
    }

    private DiscountBudgetDto toDto(DiscountBudget b) {
        return DiscountBudgetDto.builder()
                .id(b.getId()).month(b.getMonth())
                .budgetAmount(b.getBudgetAmount()).spentToDate(b.getSpentToDate())
                .build();
    }
}
