package com.webappfoundation.config;

import com.webappfoundation.model.CoinLedgerEntry;
import com.webappfoundation.model.CoinStatus;
import com.webappfoundation.model.DiscountBudget;
import com.webappfoundation.model.LoyaltyEvent;
import com.webappfoundation.model.LoyaltyEventStatus;
import com.webappfoundation.model.LoyaltyEventType;
import com.webappfoundation.model.LoyaltyProfile;
import com.webappfoundation.model.Offer;
import com.webappfoundation.model.OfferStatus;
import com.webappfoundation.model.OfferTrigger;
import com.webappfoundation.model.OfferType;
import com.webappfoundation.model.User;
import com.webappfoundation.repository.CoinLedgerEntryRepository;
import com.webappfoundation.repository.DiscountBudgetRepository;
import com.webappfoundation.repository.LoyaltyEventRepository;
import com.webappfoundation.repository.LoyaltyProfileRepository;
import com.webappfoundation.repository.OfferRepository;
import com.webappfoundation.repository.UserRepository;
import com.webappfoundation.service.LoyaltyScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * DEV-ONLY demo data for the Discount &amp; Loyalty section, so the admin screens + customer views have
 * realistic numbers to show. Seeds loyalty-domain rows only (profiles, scoring events, coin ledger,
 * discount codes, this month's budget) — it NEVER creates login users (that stays {@link AdminInitializer}'s
 * job): it attaches profiles to whatever users already exist and adds synthetic demo profiles for volume.
 * Idempotent (skips once any profile exists) and gated to the {@code dev} profile. Runs after
 * {@link LoyaltySeedInitializer} so tiers/rules/settings are present.
 */
@Component
@Profile("dev")
@Order(30)
public class LoyaltyDemoSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyDemoSeeder.class);

    private final LoyaltyProfileRepository profileRepository;
    private final LoyaltyEventRepository eventRepository;
    private final CoinLedgerEntryRepository coinRepository;
    private final OfferRepository offerRepository;
    private final DiscountBudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final LoyaltyScoreService scoreService;

    public LoyaltyDemoSeeder(LoyaltyProfileRepository profileRepository, LoyaltyEventRepository eventRepository,
                             CoinLedgerEntryRepository coinRepository, OfferRepository offerRepository,
                             DiscountBudgetRepository budgetRepository, UserRepository userRepository,
                             LoyaltyScoreService scoreService) {
        this.profileRepository = profileRepository;
        this.eventRepository = eventRepository;
        this.coinRepository = coinRepository;
        this.offerRepository = offerRepository;
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
        this.scoreService = scoreService;
    }

    /** A demo customer's target loyalty state (tier is derived from {@code score} by the recompute). */
    private record Spec(int score, int coins, String aov, int gapDays, int lastOrderDaysAgo, int returnPct, boolean holdout) {}

    // A spread that roughly matches the spec's target mix (~60% Bronze, 25% Silver, 12% Gold, 3% Platinum).
    private static final List<Spec> SPECS = List.of(
            new Spec(6200, 820, "4200", 18, 5, 2, false),   // Platinum
            new Spec(2600, 310, "2800", 24, 9, 6, false),   // Gold
            new Spec(1850, 240, "2400", 30, 14, 8, true),   // Gold (holdout)
            new Spec(1200, 150, "1900", 28, 7, 5, false),   // Silver
            new Spec(880, 95, "1600", 35, 20, 10, false),   // Silver
            new Spec(640, 70, "1500", 40, 26, 12, false),   // Silver
            new Spec(520, 55, "1400", 33, 18, 7, false),    // Silver
            new Spec(470, 40, "1300", 45, 30, 9, false),    // Bronze
            new Spec(360, 28, "1200", 50, 41, 15, false),   // Bronze
            new Spec(300, 22, "1100", 42, 22, 4, true),     // Bronze (holdout)
            new Spec(250, 18, "950", 55, 60, 18, false),    // Bronze
            new Spec(180, 12, "900", 60, 75, 22, false),    // Bronze
            new Spec(120, 8, "800", 48, 33, 11, false),     // Bronze
            new Spec(80, 5, "700", 70, 90, 45, false),      // Bronze (high return rate → code-blocked)
            new Spec(40, 2, "650", 38, 12, 3, false)        // Bronze (new)
    );

    @Override
    public void run(String... args) {
        if (profileRepository.count() > 0) {
            return;                                                 // already seeded — never duplicate
        }

        List<Integer> seededUserIds = new ArrayList<>();
        int specIdx = 0;

        // 1) Give every existing user a profile first (so the admin grid shows real emails across tiers).
        List<User> users = userRepository.findAll();
        for (User u : users) {
            Spec s = SPECS.get(specIdx % SPECS.size());
            seedCustomer(u.getId(), s);
            seededUserIds.add(u.getId());
            specIdx++;
        }

        // 2) Add synthetic demo profiles (ids well above real users) for volume across the remaining specs.
        int syntheticId = 900_001;
        while (specIdx < SPECS.size()) {
            Spec s = SPECS.get(specIdx);
            seedCustomer(syntheticId, s);
            seededUserIds.add(syntheticId);
            syntheticId++;
            specIdx++;
        }

        seedOffers(seededUserIds);
        seedBudget();

        // Make scores/tiers consistent with the seeded events (coinBalance is set directly and untouched here).
        int recomputed = scoreService.recomputeAll();
        log.info("[loyalty] demo seed: {} profiles, {} offers, 1 budget; recomputed {} profiles",
                seededUserIds.size(), offerRepository.count(), recomputed);
    }

    private void seedCustomer(Integer userId, Spec s) {
        Instant now = Instant.now();
        profileRepository.save(LoyaltyProfile.builder()
                .userId(userId)
                .coinBalance(BigDecimal.valueOf(s.coins()))
                .avgOrderValue(new BigDecimal(s.aov()))
                .usualOrderGapDays(s.gapDays())
                .lastOrderAt(now.minus(s.lastOrderDaysAgo(), ChronoUnit.DAYS))
                .returnRate(BigDecimal.valueOf(s.returnPct()).movePointLeft(2))   // pct → 0..1
                .inHoldout(s.holdout())
                .birthday(LocalDate.now().minusYears(28).withDayOfMonth(15))
                .createdAt(now.minus(180, ChronoUnit.DAYS))
                .updatedAt(now)
                .build());

        // One confirmed purchase that carries most of the score, plus a little recent engagement.
        event(userId, LoyaltyEventType.PURCHASE, s.score(), now.minus(2, ChronoUnit.DAYS));
        event(userId, LoyaltyEventType.ADD_TO_CART, 3, now.minus(1, ChronoUnit.DAYS));
        event(userId, LoyaltyEventType.PRODUCT_VIEW, 1, now.minus(1, ChronoUnit.DAYS));

        if (s.coins() > 0) {
            coinRepository.save(CoinLedgerEntry.builder()
                    .userId(userId).coins(BigDecimal.valueOf(s.coins())).reason("purchase")
                    .status(CoinStatus.CONFIRMED)
                    .earnedAt(now.minus(2, ChronoUnit.DAYS))
                    .expiresAt(now.plus(365, ChronoUnit.DAYS))
                    .build());
        }
    }

    private void event(Integer userId, LoyaltyEventType type, int points, Instant createdAt) {
        eventRepository.save(LoyaltyEvent.builder()
                .userId(userId).type(type).points(BigDecimal.valueOf(points))
                .status(LoyaltyEventStatus.CONFIRMED).createdAt(createdAt).build());
    }

    private void seedOffers(List<Integer> userIds) {
        Instant now = Instant.now();
        // Public festive campaign code.
        offerRepository.save(Offer.builder()
                .code("DIWALI10").userId(null).trigger(OfferTrigger.FESTIVE).type(OfferType.PCT)
                .value(new BigDecimal("10")).minSpend(new BigDecimal("999")).maxDiscount(new BigDecimal("300"))
                .maxUses(500).usedCount(137).campaignId("diwali-2026").status(OfferStatus.ACTIVE)
                .startsAt(now.minus(2, ChronoUnit.DAYS)).expiresAt(now.plus(20, ChronoUnit.DAYS))
                .createdAt(now.minus(2, ChronoUnit.DAYS)).build());

        // A few engine-issued personal codes for the first seeded customers.
        if (!userIds.isEmpty()) {
            personal(userIds.get(0), "WB-7K4QXR", OfferTrigger.WIN_BACK, OfferType.PCT, "10", "3000", "300",
                    OfferStatus.ACTIVE, 0, now.plus(7, ChronoUnit.DAYS));
        }
        if (userIds.size() > 1) {
            personal(userIds.get(1), "BDAY-M9P2TC", OfferTrigger.BIRTHDAY, OfferType.COINS, "100", null, null,
                    OfferStatus.ACTIVE, 0, now.plus(30, ChronoUnit.DAYS));
        }
        if (userIds.size() > 2) {
            personal(userIds.get(2), "WEL-5H8KQ2", OfferTrigger.WELCOME, OfferType.PCT, "10", null, "300",
                    OfferStatus.USED, 1, now.minus(1, ChronoUnit.DAYS));
        }
    }

    private void personal(Integer userId, String code, OfferTrigger trigger, OfferType type, String value,
                          String minSpend, String maxDiscount, OfferStatus status, int usedCount, Instant expiresAt) {
        offerRepository.save(Offer.builder()
                .code(code).userId(userId).trigger(trigger).rung(1).type(type)
                .value(value == null ? null : new BigDecimal(value))
                .minSpend(minSpend == null ? null : new BigDecimal(minSpend))
                .maxDiscount(maxDiscount == null ? null : new BigDecimal(maxDiscount))
                .maxUses(1).usedCount(usedCount).status(status)
                .startsAt(Instant.now().minus(2, ChronoUnit.DAYS)).expiresAt(expiresAt)
                .createdAt(Instant.now().minus(2, ChronoUnit.DAYS)).build());
    }

    private void seedBudget() {
        String month = YearMonth.now().toString();                 // yyyy-MM
        if (budgetRepository.findByMonth(month).isEmpty()) {
            budgetRepository.save(DiscountBudget.builder()
                    .month(month).budgetAmount(new BigDecimal("80000")).spentToDate(new BigDecimal("18500"))
                    .build());
        }
    }
}
