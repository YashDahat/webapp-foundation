package com.webappfoundation.loyalty;

import com.webappfoundation.model.LoyaltyEvent;
import com.webappfoundation.model.LoyaltyEventStatus;
import com.webappfoundation.model.LoyaltyEventType;
import com.webappfoundation.model.LoyaltyProfile;
import com.webappfoundation.model.Tier;
import com.webappfoundation.repository.LoyaltyEventRepository;
import com.webappfoundation.repository.LoyaltyProfileRepository;
import com.webappfoundation.service.LoyaltyEventService;
import com.webappfoundation.service.LoyaltyNightlyJob;
import com.webappfoundation.service.LoyaltyScoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Phase 1 Loyalty Score engine against the design spec: one-time signup points, per-type
 * daily caps, the 90-day half-life decay, tier assignment, and nightly confirmation of pending events.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LoyaltyScoreEngineTest {

    @Autowired LoyaltyEventService eventService;
    @Autowired LoyaltyScoreService scoreService;
    @Autowired LoyaltyNightlyJob nightlyJob;
    @Autowired LoyaltyEventRepository eventRepository;
    @Autowired LoyaltyProfileRepository profileRepository;

    @Test
    void signupPointsAwardedExactlyOnce() {
        Integer userId = 900001;
        eventService.ensureBootstrap(userId);
        eventService.ensureBootstrap(userId);                       // idempotent
        long signups = eventRepository.findByUserIdAndStatus(userId, LoyaltyEventStatus.CONFIRMED).stream()
                .filter(e -> e.getType() == LoyaltyEventType.SIGNUP).count();
        assertThat(signups).isEqualTo(1);

        LoyaltyProfile p = scoreService.recompute(userId);
        assertThat(p.getScore()).isEqualByComparingTo(new BigDecimal("50.00"));   // SIGNUP = 50, age ~0 → no decay
        assertThat(p.getTier()).isEqualTo(Tier.BRONZE);
    }

    @Test
    void perTypeDailyCapIsEnforced() {
        Integer userId = 900002;
        // ADD_TO_CART is 3 points, capped at 5/day → at most 5 are recorded regardless of attempts.
        for (int i = 0; i < 12; i++) {
            eventService.record(userId, LoyaltyEventType.ADD_TO_CART, null, null);
        }
        long addToCarts = eventRepository.findByUserIdAndStatus(userId, LoyaltyEventStatus.CONFIRMED).stream()
                .filter(e -> e.getType() == LoyaltyEventType.ADD_TO_CART).count();
        assertThat(addToCarts).isEqualTo(5);
    }

    @Test
    void scoreDecaysWithNinetyDayHalfLife() {
        Integer userId = 900003;
        profileRepository.save(LoyaltyProfile.builder()
                .userId(userId).createdAt(Instant.now()).updatedAt(Instant.now()).build());
        // A 1000-point event aged exactly 90 days should contribute ~500 after a single half-life.
        eventRepository.save(LoyaltyEvent.builder()
                .userId(userId).type(LoyaltyEventType.PURCHASE).points(new BigDecimal("1000"))
                .status(LoyaltyEventStatus.CONFIRMED)
                .createdAt(Instant.now().minus(90, ChronoUnit.DAYS)).build());

        LoyaltyProfile p = scoreService.recompute(userId);
        assertThat(p.getScore().doubleValue()).isCloseTo(500.0, org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    void tierRisesWithScore() {
        Integer userId = 900004;
        profileRepository.save(LoyaltyProfile.builder()
                .userId(userId).createdAt(Instant.now()).updatedAt(Instant.now()).build());
        eventRepository.save(LoyaltyEvent.builder()
                .userId(userId).type(LoyaltyEventType.PURCHASE).points(new BigDecimal("5000"))
                .status(LoyaltyEventStatus.CONFIRMED).createdAt(Instant.now()).build());

        LoyaltyProfile p = scoreService.recompute(userId);
        assertThat(p.getTier()).isEqualTo(Tier.PLATINUM);           // >= 4000
    }

    @Test
    void nightlyConfirmsPendingPurchaseThenItCountsTowardScore() {
        Integer userId = 900005;
        profileRepository.save(LoyaltyProfile.builder()
                .userId(userId).createdAt(Instant.now()).updatedAt(Instant.now()).build());
        // A pending purchase whose return window already closed.
        eventRepository.save(LoyaltyEvent.builder()
                .userId(userId).type(LoyaltyEventType.PURCHASE).points(new BigDecimal("600"))
                .status(LoyaltyEventStatus.PENDING)
                .createdAt(Instant.now().minus(8, ChronoUnit.DAYS))
                .confirmAt(Instant.now().minus(1, ChronoUnit.DAYS)).build());

        nightlyJob.runNow();

        LoyaltyProfile p = profileRepository.findById(userId).orElseThrow();
        assertThat(p.getScore().doubleValue()).isGreaterThan(0.0); // pending→confirmed now counts
    }
}
