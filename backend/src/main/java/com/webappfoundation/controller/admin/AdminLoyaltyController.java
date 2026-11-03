package com.webappfoundation.controller.admin;

import com.webappfoundation.dto.DiscountBudgetDto;
import com.webappfoundation.dto.LoyaltyEventRuleDto;
import com.webappfoundation.dto.LoyaltyProfileDto;
import com.webappfoundation.dto.LoyaltySettingsDto;
import com.webappfoundation.dto.LoyaltyTierDto;
import com.webappfoundation.dto.LoyaltyMetricsDto;
import com.webappfoundation.dto.LoyaltyTriggerConfigDto;
import com.webappfoundation.dto.UpdateLoyaltyEventRuleRequest;
import com.webappfoundation.dto.UpdateLoyaltyTierRequest;
import com.webappfoundation.dto.UpdateLoyaltyTriggerConfigRequest;
import com.webappfoundation.dto.UpsertDiscountBudgetRequest;
import com.webappfoundation.service.DiscountBudgetService;
import com.webappfoundation.service.LoyaltyConfigService;
import com.webappfoundation.service.LoyaltyMetricsService;
import com.webappfoundation.service.LoyaltyNightlyJob;
import com.webappfoundation.service.LoyaltyProfileService;
import com.webappfoundation.service.LoyaltySettingsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin control panel for the Discount &amp; Loyalty programme: the master settings (incl. the
 * {@code discountsEnabled} kill switch), the seeded tier / event-rule / trigger config, the monthly
 * budget, and the read-only customer-loyalty grid. ADMIN-gated by SecurityConfig ({@code /api/v1/admin/**});
 * all writes are audited inside the services.
 */
@RestController
@RequestMapping("/api/v1/admin/loyalty")
public class AdminLoyaltyController {

    private final LoyaltySettingsService settingsService;
    private final LoyaltyConfigService configService;
    private final DiscountBudgetService budgetService;
    private final LoyaltyProfileService profileService;
    private final LoyaltyNightlyJob nightlyJob;
    private final LoyaltyMetricsService metricsService;

    public AdminLoyaltyController(LoyaltySettingsService settingsService, LoyaltyConfigService configService,
                                  DiscountBudgetService budgetService, LoyaltyProfileService profileService,
                                  LoyaltyNightlyJob nightlyJob, LoyaltyMetricsService metricsService) {
        this.settingsService = settingsService;
        this.configService = configService;
        this.budgetService = budgetService;
        this.profileService = profileService;
        this.nightlyJob = nightlyJob;
        this.metricsService = metricsService;
    }

    @GetMapping("/metrics")
    public LoyaltyMetricsDto metrics() {
        return metricsService.metrics();
    }

    // ── Settings (incl. discountsEnabled master kill switch) ──

    @GetMapping("/settings")
    public LoyaltySettingsDto getSettings() {
        return settingsService.getDto();
    }

    @PutMapping("/settings")
    public LoyaltySettingsDto updateSettings(@RequestBody LoyaltySettingsDto request) {
        return settingsService.update(request);
    }

    // ── Tiers ──

    @GetMapping("/tiers")
    public List<LoyaltyTierDto> listTiers() {
        return configService.listTiers();
    }

    @PutMapping("/tiers/{id}")
    public LoyaltyTierDto updateTier(@PathVariable Long id, @RequestBody UpdateLoyaltyTierRequest request) {
        return configService.updateTier(id, request);
    }

    // ── Event scoring rules ──

    @GetMapping("/event-rules")
    public List<LoyaltyEventRuleDto> listEventRules() {
        return configService.listEventRules();
    }

    @PutMapping("/event-rules/{id}")
    public LoyaltyEventRuleDto updateEventRule(@PathVariable Long id,
                                               @RequestBody UpdateLoyaltyEventRuleRequest request) {
        return configService.updateEventRule(id, request);
    }

    // ── Triggers ──

    @GetMapping("/triggers")
    public List<LoyaltyTriggerConfigDto> listTriggers() {
        return configService.listTriggers();
    }

    @PutMapping("/triggers/{id}")
    public LoyaltyTriggerConfigDto updateTrigger(@PathVariable Long id,
                                                 @RequestBody UpdateLoyaltyTriggerConfigRequest request) {
        return configService.updateTrigger(id, request);
    }

    // ── Monthly discount budget ──

    @GetMapping("/budgets")
    public List<DiscountBudgetDto> listBudgets() {
        return budgetService.adminList();
    }

    @PostMapping("/budgets")
    public DiscountBudgetDto upsertBudget(@RequestBody UpsertDiscountBudgetRequest request) {
        return budgetService.upsert(request);
    }

    // ── Customers (read-only loyalty grid) ──

    @GetMapping("/customers")
    public List<LoyaltyProfileDto> listCustomers() {
        return profileService.adminList();
    }

    /** Run the nightly maintenance now (confirm due events + recompute all scores/tiers) — for ops/testing. */
    @PostMapping("/run-nightly")
    public Map<String, Integer> runNightly() {
        return Map.of("profilesRecomputed", nightlyJob.runNow());
    }
}
