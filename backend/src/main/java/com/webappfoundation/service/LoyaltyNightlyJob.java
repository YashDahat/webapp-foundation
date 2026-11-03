package com.webappfoundation.service;

import com.webappfoundation.model.LoyaltyEvent;
import com.webappfoundation.model.LoyaltyEventStatus;
import com.webappfoundation.repository.LoyaltyEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The nightly loyalty maintenance job: confirm purchase events whose return window has closed, then
 * recompute every customer's score + tier (with decay and the 30-day downgrade grace). Coin confirmation
 * and expiry are added by Phase 2. Runs on {@code loyalty.nightly-cron} (default 03:00 daily); also
 * invokable on demand via {@link #runNow()} (admin button, for testing/ops).
 */
@Component
public class LoyaltyNightlyJob {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyNightlyJob.class);

    private final LoyaltyEventRepository eventRepository;
    private final LoyaltyScoreService scoreService;
    private final CoinService coinService;
    private final OfferService offerService;
    private final com.webappfoundation.repository.LoyaltyProfileRepository profileRepository;

    public LoyaltyNightlyJob(LoyaltyEventRepository eventRepository, LoyaltyScoreService scoreService,
                             CoinService coinService, OfferService offerService,
                             com.webappfoundation.repository.LoyaltyProfileRepository profileRepository) {
        this.eventRepository = eventRepository;
        this.scoreService = scoreService;
        this.coinService = coinService;
        this.offerService = offerService;
        this.profileRepository = profileRepository;
    }

    @Scheduled(cron = "${loyalty.nightly-cron:0 0 3 * * *}")
    public void nightly() {
        runNow();
    }

    /** Confirm due purchase events, then recompute all scores/tiers. Returns the number of profiles processed. */
    @Transactional
    public int runNow() {
        int confirmed = confirmDueEvents();
        int coinsConfirmed = coinService.confirmDueEarnings();
        int coinsExpired = coinService.expireDueCoins();
        int profiles = scoreService.recomputeAll();
        // Fire time-based offer triggers (win-back / birthday) off the freshly recomputed profiles.
        profileRepository.findAll().forEach(offerService::checkTimeTriggers);
        log.info("[loyalty] nightly: confirmed {} events, {} coin earnings; expired {} coins; recomputed {} profiles",
                confirmed, coinsConfirmed, coinsExpired, profiles);
        return profiles;
    }

    private int confirmDueEvents() {
        List<LoyaltyEvent> due = eventRepository.findByStatusAndConfirmAtBefore(LoyaltyEventStatus.PENDING, Instant.now());
        for (LoyaltyEvent e : due) {
            e.setStatus(LoyaltyEventStatus.CONFIRMED);
        }
        eventRepository.saveAll(due);
        return due.size();
    }
}
