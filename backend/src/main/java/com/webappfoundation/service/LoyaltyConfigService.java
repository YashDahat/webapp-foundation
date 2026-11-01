package com.webappfoundation.service;

import com.webappfoundation.dto.LoyaltyEventRuleDto;
import com.webappfoundation.dto.LoyaltyTierDto;
import com.webappfoundation.dto.LoyaltyTriggerConfigDto;
import com.webappfoundation.dto.UpdateLoyaltyEventRuleRequest;
import com.webappfoundation.dto.UpdateLoyaltyTierRequest;
import com.webappfoundation.dto.UpdateLoyaltyTriggerConfigRequest;
import com.webappfoundation.exception.ResourceNotFoundException;
import com.webappfoundation.model.LoyaltyEventRule;
import com.webappfoundation.model.LoyaltyTier;
import com.webappfoundation.model.LoyaltyTriggerConfig;
import com.webappfoundation.repository.LoyaltyEventRuleRepository;
import com.webappfoundation.repository.LoyaltyTierRepository;
import com.webappfoundation.repository.LoyaltyTriggerConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Admin read/update for the three seeded loyalty config tables — tiers, event scoring rules, and
 * triggers. The config keys themselves (the four tiers, the fixed event types, the fixed triggers) are
 * seeded and never created/deleted here; admins only tune their values. All writes are audited.
 */
@Service
public class LoyaltyConfigService {

    private final LoyaltyTierRepository tierRepository;
    private final LoyaltyEventRuleRepository eventRuleRepository;
    private final LoyaltyTriggerConfigRepository triggerRepository;
    private final AuditService auditService;

    public LoyaltyConfigService(LoyaltyTierRepository tierRepository,
                                LoyaltyEventRuleRepository eventRuleRepository,
                                LoyaltyTriggerConfigRepository triggerRepository,
                                AuditService auditService) {
        this.tierRepository = tierRepository;
        this.eventRuleRepository = eventRuleRepository;
        this.triggerRepository = triggerRepository;
        this.auditService = auditService;
    }

    // ── Tiers ──

    public List<LoyaltyTierDto> listTiers() {
        return tierRepository.findAllByOrderByMinScoreAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public LoyaltyTierDto updateTier(Long id, UpdateLoyaltyTierRequest req) {
        LoyaltyTier t = tierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loyalty tier not found: " + id));
        if (req.getName() != null) t.setName(req.getName().trim());
        if (req.getMinScore() != null) t.setMinScore(req.getMinScore());
        if (req.getCoinsPer100() != null) t.setCoinsPer100(req.getCoinsPer100());
        if (req.getPerks() != null) t.setPerks(req.getPerks());
        LoyaltyTier saved = tierRepository.save(t);
        auditService.record(AuditActions.LOYALTY_TIER_UPDATED, AuditActions.SUCCESS, "LOYALTY_TIER",
                String.valueOf(saved.getId()), "tier=" + saved.getTier() + " minScore=" + saved.getMinScore());
        return toDto(saved);
    }

    // ── Event rules ──

    public List<LoyaltyEventRuleDto> listEventRules() {
        return eventRuleRepository.findAll().stream()
                .sorted(Comparator.comparing(r -> r.getEventType().name()))
                .map(this::toDto).toList();
    }

    @Transactional
    public LoyaltyEventRuleDto updateEventRule(Long id, UpdateLoyaltyEventRuleRequest req) {
        LoyaltyEventRule r = eventRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loyalty event rule not found: " + id));
        if (req.getPoints() != null) r.setPoints(req.getPoints());
        r.setPerDayCap(req.getPerDayCap());
        if (req.getOnceOnly() != null) r.setOnceOnly(req.getOnceOnly());
        if (req.getEarnsCoins() != null) r.setEarnsCoins(req.getEarnsCoins());
        LoyaltyEventRule saved = eventRuleRepository.save(r);
        auditService.record(AuditActions.LOYALTY_EVENT_RULE_UPDATED, AuditActions.SUCCESS, "LOYALTY_EVENT_RULE",
                String.valueOf(saved.getId()), "type=" + saved.getEventType() + " points=" + saved.getPoints());
        return toDto(saved);
    }

    // ── Triggers ──

    public List<LoyaltyTriggerConfigDto> listTriggers() {
        return triggerRepository.findAll().stream()
                .sorted(Comparator.comparing(t -> t.getTrigger().name()))
                .map(this::toDto).toList();
    }

    @Transactional
    public LoyaltyTriggerConfigDto updateTrigger(Long id, UpdateLoyaltyTriggerConfigRequest req) {
        LoyaltyTriggerConfig t = triggerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loyalty trigger not found: " + id));
        if (req.getEnabled() != null) t.setEnabled(req.getEnabled());
        if (req.getRungsJson() != null) t.setRungsJson(req.getRungsJson());
        if (req.getExpiryDays() != null) t.setExpiryDays(req.getExpiryDays());
        LoyaltyTriggerConfig saved = triggerRepository.save(t);
        auditService.record(AuditActions.LOYALTY_TRIGGER_UPDATED, AuditActions.SUCCESS, "LOYALTY_TRIGGER",
                String.valueOf(saved.getId()), "trigger=" + saved.getTrigger() + " enabled=" + saved.isEnabled());
        return toDto(saved);
    }

    // ── Mappers ──

    private LoyaltyTierDto toDto(LoyaltyTier t) {
        return LoyaltyTierDto.builder()
                .id(t.getId()).tier(t.getTier()).name(t.getName())
                .minScore(t.getMinScore()).coinsPer100(t.getCoinsPer100()).perks(t.getPerks())
                .build();
    }

    private LoyaltyEventRuleDto toDto(LoyaltyEventRule r) {
        return LoyaltyEventRuleDto.builder()
                .id(r.getId()).eventType(r.getEventType()).points(r.getPoints())
                .perDayCap(r.getPerDayCap()).onceOnly(r.isOnceOnly()).earnsCoins(r.isEarnsCoins())
                .build();
    }

    private LoyaltyTriggerConfigDto toDto(LoyaltyTriggerConfig t) {
        return LoyaltyTriggerConfigDto.builder()
                .id(t.getId()).trigger(t.getTrigger()).enabled(t.isEnabled())
                .rungsJson(t.getRungsJson()).expiryDays(t.getExpiryDays())
                .build();
    }
}
