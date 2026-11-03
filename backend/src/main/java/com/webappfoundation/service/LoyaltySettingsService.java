package com.webappfoundation.service;

import com.webappfoundation.dto.LoyaltySettingsDto;
import com.webappfoundation.model.LoyaltySettings;
import com.webappfoundation.repository.LoyaltySettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads/writes the single {@link LoyaltySettings} row (id = 1), including the client-controllable
 * {@code discountsEnabled} master kill switch. The engine reads {@link #get()} (entity) to gate the
 * discount/promotion side; admins read/update via the DTO. The row is created on first access if the
 * seed initializer has not run yet.
 */
@Service
public class LoyaltySettingsService {

    static final long SINGLETON_ID = 1L;

    private final LoyaltySettingsRepository repository;
    private final AuditService auditService;

    public LoyaltySettingsService(LoyaltySettingsRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /** The settings entity, creating the defaults row if absent. Used by the engine for gating/knobs. */
    @Transactional
    public LoyaltySettings get() {
        return repository.findById(SINGLETON_ID)
                .orElseGet(() -> repository.save(LoyaltySettings.builder().id(SINGLETON_ID).build()));
    }

    /** Convenience gate used across the discount/promotion side. */
    public boolean discountsEnabled() {
        return get().isDiscountsEnabled();
    }

    public LoyaltySettingsDto getDto() {
        return toDto(get());
    }

    @Transactional
    public LoyaltySettingsDto update(LoyaltySettingsDto req) {
        LoyaltySettings s = get();
        boolean wasEnabled = s.isDiscountsEnabled();
        s.setDiscountsEnabled(req.isDiscountsEnabled());
        if (req.getCoinValue() != null) s.setCoinValue(req.getCoinValue());
        if (req.getMaxCoinPctOfOrder() != null) s.setMaxCoinPctOfOrder(req.getMaxCoinPctOfOrder());
        if (req.getMinOrderForCoins() != null) s.setMinOrderForCoins(req.getMinOrderForCoins());
        if (req.getCoinExpiryMonths() != null) s.setCoinExpiryMonths(req.getCoinExpiryMonths());
        if (req.getScoreHalfLifeDays() != null) s.setScoreHalfLifeDays(req.getScoreHalfLifeDays());
        if (req.getDailyCap() != null) s.setDailyCap(req.getDailyCap());
        if (req.getMonthlyCap() != null) s.setMonthlyCap(req.getMonthlyCap());
        if (req.getHoldoutPct() != null) s.setHoldoutPct(req.getHoldoutPct());
        if (req.getDiscountPctTarget() != null) s.setDiscountPctTarget(req.getDiscountPctTarget());
        if (req.getReturnRateBlockPct() != null) s.setReturnRateBlockPct(req.getReturnRateBlockPct());
        if (req.getNewCodeCooldownDays() != null) s.setNewCodeCooldownDays(req.getNewCodeCooldownDays());
        if (req.getReturnWindowDays() != null) s.setReturnWindowDays(req.getReturnWindowDays());
        if (req.getMarginFloorPct() != null) s.setMarginFloorPct(req.getMarginFloorPct());
        LoyaltySettings saved = repository.save(s);
        auditService.record(AuditActions.LOYALTY_SETTINGS_UPDATED, AuditActions.SUCCESS, "LOYALTY_SETTINGS",
                String.valueOf(SINGLETON_ID),
                "discountsEnabled=" + wasEnabled + "->" + saved.isDiscountsEnabled());
        return toDto(saved);
    }

    private LoyaltySettingsDto toDto(LoyaltySettings s) {
        return LoyaltySettingsDto.builder()
                .discountsEnabled(s.isDiscountsEnabled())
                .coinValue(s.getCoinValue())
                .maxCoinPctOfOrder(s.getMaxCoinPctOfOrder())
                .minOrderForCoins(s.getMinOrderForCoins())
                .coinExpiryMonths(s.getCoinExpiryMonths())
                .scoreHalfLifeDays(s.getScoreHalfLifeDays())
                .dailyCap(s.getDailyCap())
                .monthlyCap(s.getMonthlyCap())
                .holdoutPct(s.getHoldoutPct())
                .discountPctTarget(s.getDiscountPctTarget())
                .returnRateBlockPct(s.getReturnRateBlockPct())
                .newCodeCooldownDays(s.getNewCodeCooldownDays())
                .returnWindowDays(s.getReturnWindowDays())
                .marginFloorPct(s.getMarginFloorPct())
                .build();
    }
}
