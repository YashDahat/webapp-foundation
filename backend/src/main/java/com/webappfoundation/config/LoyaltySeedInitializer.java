package com.webappfoundation.config;

import com.webappfoundation.model.LoyaltyEventRule;
import com.webappfoundation.model.LoyaltyEventType;
import com.webappfoundation.model.LoyaltySettings;
import com.webappfoundation.model.LoyaltyTier;
import com.webappfoundation.model.LoyaltyTriggerConfig;
import com.webappfoundation.model.OfferTrigger;
import com.webappfoundation.model.Tier;
import com.webappfoundation.repository.LoyaltyEventRuleRepository;
import com.webappfoundation.repository.LoyaltySettingsRepository;
import com.webappfoundation.repository.LoyaltyTierRepository;
import com.webappfoundation.repository.LoyaltyTriggerConfigRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Seeds the Discount &amp; Loyalty config tables from the design spec on startup: the four tiers, the
 * event scoring rules, the trigger offer-ladders, and the global settings singleton (with the master
 * {@code discountsEnabled} kill switch defaulting to on). Idempotent — each row is inserted only if its
 * key is absent, so admin edits are never overwritten. There is no Flyway; schema is {@code ddl-auto}.
 */
@Component
@Order(20)
public class LoyaltySeedInitializer implements CommandLineRunner {

    private final LoyaltyTierRepository tierRepository;
    private final LoyaltyEventRuleRepository eventRuleRepository;
    private final LoyaltyTriggerConfigRepository triggerRepository;
    private final LoyaltySettingsRepository settingsRepository;

    public LoyaltySeedInitializer(LoyaltyTierRepository tierRepository,
                                  LoyaltyEventRuleRepository eventRuleRepository,
                                  LoyaltyTriggerConfigRepository triggerRepository,
                                  LoyaltySettingsRepository settingsRepository) {
        this.tierRepository = tierRepository;
        this.eventRuleRepository = eventRuleRepository;
        this.triggerRepository = triggerRepository;
        this.settingsRepository = settingsRepository;
    }

    @Override
    public void run(String... args) {
        seedSettings();
        seedTiers();
        seedEventRules();
        seedTriggers();
    }

    private void seedSettings() {
        if (settingsRepository.findById(1L).isEmpty()) {
            settingsRepository.save(LoyaltySettings.builder().id(1L).build());
        }
    }

    private void seedTiers() {
        tier(Tier.BRONZE, "Bronze", 0, "2", "Birthday coins, welcome offer");
        tier(Tier.SILVER, "Silver", 500, "2.5", "Free shipping above ₹999, early sale access (12 hours)");
        tier(Tier.GOLD, "Gold", 1500, "3", "Free shipping always, 24-hour early access, 1 surprise gift a quarter");
        tier(Tier.PLATINUM, "Platinum", 4000, "4", "Priority support, exclusive drops, free returns, personal offers");
    }

    private void tier(Tier tier, String name, int minScore, String coinsPer100, String perks) {
        if (!tierRepository.existsByTier(tier)) {
            tierRepository.save(LoyaltyTier.builder()
                    .tier(tier).name(name).minScore(minScore)
                    .coinsPer100(new BigDecimal(coinsPer100)).perks(perks).build());
        }
    }

    private void seedEventRules() {
        // eventType, points, perDayCap (null = none), onceOnly, earnsCoins
        rule(LoyaltyEventType.SIGNUP, "50", null, true, false);
        rule(LoyaltyEventType.PROFILE_COMPLETE, "30", null, true, false);
        rule(LoyaltyEventType.DAILY_VISIT, "2", 1, false, false);
        rule(LoyaltyEventType.PRODUCT_VIEW, "0.5", 10, false, false);
        rule(LoyaltyEventType.WISHLIST, "2", 5, false, false);
        rule(LoyaltyEventType.ADD_TO_CART, "3", 5, false, false);
        rule(LoyaltyEventType.PURCHASE, "10", null, false, true);   // 10 points per ₹1,000 paid
        rule(LoyaltyEventType.REVIEW, "20", null, false, true);     // 1 per item (enforced per-item in engine)
        rule(LoyaltyEventType.REVIEW_MEDIA, "35", null, false, true);
        rule(LoyaltyEventType.SHARE_CLICK, "5", 3, false, false);
        rule(LoyaltyEventType.REFERRAL_SIGNUP, "50", null, false, false);   // 20/month (monthly cap in engine)
        rule(LoyaltyEventType.REFERRAL_PURCHASE, "200", null, false, true);
        rule(LoyaltyEventType.NOTIF_OPT_IN, "25", null, true, false);
    }

    private void rule(LoyaltyEventType type, String points, Integer perDayCap, boolean onceOnly, boolean earnsCoins) {
        if (!eventRuleRepository.existsByEventType(type)) {
            eventRuleRepository.save(LoyaltyEventRule.builder()
                    .eventType(type).points(new BigDecimal(points))
                    .perDayCap(perDayCap).onceOnly(onceOnly).earnsCoins(earnsCoins).build());
        }
    }

    private void seedTriggers() {
        // trigger, expiryDays, rungs JSON (cheapest first)
        trigger(OfferTrigger.WELCOME, 7,
                "[{\"type\":\"FREE_SHIP\"},{\"type\":\"PCT\",\"value\":10,\"maxDiscount\":300}]");
        trigger(OfferTrigger.ABANDONED_CART, 2,
                "[{\"type\":\"REMINDER\"},{\"type\":\"FREE_SHIP\"},{\"type\":\"PCT\",\"value\":5}]");
        trigger(OfferTrigger.WIN_BACK, 7,
                "[{\"type\":\"COINS\",\"double\":true},{\"type\":\"PCT\",\"value\":10},{\"type\":\"PCT\",\"value\":15}]");
        trigger(OfferTrigger.TIER_UP, 14, "[{\"type\":\"GIFT\"},{\"type\":\"COINS\"}]");
        trigger(OfferTrigger.MILESTONE, 14, "[{\"type\":\"GIFT\"},{\"type\":\"COINS\"}]");
        trigger(OfferTrigger.BIRTHDAY, 30,
                "[{\"type\":\"COINS\"},{\"type\":\"PCT\",\"value\":10,\"minTier\":\"GOLD\"}]");
        trigger(OfferTrigger.SLOW_STOCK, 3,
                "[{\"type\":\"PCT\",\"value\":15},{\"type\":\"PCT\",\"value\":30}]");
        trigger(OfferTrigger.FESTIVE, 7, "[{\"type\":\"PCT\",\"value\":10}]");
        trigger(OfferTrigger.SURPRISE, 14, "[{\"type\":\"GIFT\"},{\"type\":\"COINS\",\"value\":100}]");
    }

    private void trigger(OfferTrigger trigger, int expiryDays, String rungsJson) {
        if (!triggerRepository.existsByTrigger(trigger)) {
            triggerRepository.save(LoyaltyTriggerConfig.builder()
                    .trigger(trigger).enabled(true).expiryDays(expiryDays).rungsJson(rungsJson).build());
        }
    }
}
