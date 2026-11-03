package com.webappfoundation.service;

import com.webappfoundation.model.LoyaltyEvent;
import com.webappfoundation.model.LoyaltyEventStatus;
import com.webappfoundation.model.LoyaltyProfile;
import com.webappfoundation.model.LoyaltySettings;
import com.webappfoundation.model.LoyaltyTier;
import com.webappfoundation.model.Tier;
import com.webappfoundation.repository.LoyaltyEventRepository;
import com.webappfoundation.repository.LoyaltyProfileRepository;
import com.webappfoundation.repository.LoyaltyTierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Recomputes a customer's Loyalty Score (Σ points · ½^(age/half-life)) from their CONFIRMED events and
 * moves their tier: up instantly, down only after a 30-day grace window (a pending downgrade that a
 * recovery can cancel). Driven nightly by {@link LoyaltyNightlyJob}; also recomputed on demand.
 */
@Service
public class LoyaltyScoreService {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyScoreService.class);
    private static final int DOWNGRADE_GRACE_DAYS = 30;

    private final LoyaltyProfileRepository profileRepository;
    private final LoyaltyEventRepository eventRepository;
    private final LoyaltyTierRepository tierRepository;
    private final LoyaltySettingsService settingsService;

    public LoyaltyScoreService(LoyaltyProfileRepository profileRepository, LoyaltyEventRepository eventRepository,
                               LoyaltyTierRepository tierRepository, LoyaltySettingsService settingsService) {
        this.profileRepository = profileRepository;
        this.eventRepository = eventRepository;
        this.tierRepository = tierRepository;
        this.settingsService = settingsService;
    }

    /** Recompute every profile's score + tier. Returns how many were processed. */
    @Transactional
    public int recomputeAll() {
        List<LoyaltyTier> tiers = tierRepository.findAllByOrderByMinScoreAsc();
        int halfLife = settingsService.get().getScoreHalfLifeDays();
        Instant now = Instant.now();
        List<LoyaltyProfile> profiles = profileRepository.findAll();
        for (LoyaltyProfile p : profiles) {
            recompute(p, tiers, halfLife, now);
        }
        profileRepository.saveAll(profiles);
        return profiles.size();
    }

    /** Recompute a single profile (loads tiers/settings itself) — used for on-demand refresh. */
    @Transactional
    public LoyaltyProfile recompute(Integer userId) {
        LoyaltyProfile p = profileRepository.findById(userId).orElse(null);
        if (p == null) return null;
        recompute(p, tierRepository.findAllByOrderByMinScoreAsc(), settingsService.get().getScoreHalfLifeDays(), Instant.now());
        return profileRepository.save(p);
    }

    private void recompute(LoyaltyProfile p, List<LoyaltyTier> tiers, int halfLife, Instant now) {
        List<LoyaltyEvent> events = eventRepository.findByUserIdAndStatus(p.getUserId(), LoyaltyEventStatus.CONFIRMED);
        double score = 0.0;
        for (LoyaltyEvent e : events) {
            double ageDays = Duration.between(e.getCreatedAt(), now).toSeconds() / 86_400.0;
            score += e.getPoints().doubleValue() * Math.pow(0.5, Math.max(0, ageDays) / halfLife);
        }
        BigDecimal newScore = BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
        p.setScore(newScore);
        p.setScoreUpdatedAt(now);
        applyTier(p, tierForScore(newScore, tiers), now);
        p.setUpdatedAt(now);
    }

    private void applyTier(LoyaltyProfile p, Tier target, Instant now) {
        Tier current = p.getTier() == null ? Tier.BRONZE : p.getTier();
        if (target.ordinal() > current.ordinal()) {
            // Upgrade is instant; cancel any pending downgrade.
            p.setTier(target);
            clearPending(p);
            log.info("[loyalty] user {} upgraded {} -> {}", p.getUserId(), current, target);
        } else if (target.ordinal() < current.ordinal()) {
            if (p.getPendingDowngradeTier() != target || p.getDowngradeEffectiveAt() == null) {
                // Start (or re-target) the 30-day grace window.
                p.setPendingDowngradeTier(target);
                p.setDowngradeEffectiveAt(now.plus(DOWNGRADE_GRACE_DAYS, ChronoUnit.DAYS));
            } else if (!now.isBefore(p.getDowngradeEffectiveAt())) {
                // Grace elapsed and still below — apply the downgrade.
                p.setTier(target);
                clearPending(p);
                log.info("[loyalty] user {} downgraded {} -> {}", p.getUserId(), current, target);
            }
        } else {
            clearPending(p);                                        // recovered into the current tier
        }
    }

    private void clearPending(LoyaltyProfile p) {
        p.setPendingDowngradeTier(null);
        p.setDowngradeEffectiveAt(null);
    }

    private Tier tierForScore(BigDecimal score, List<LoyaltyTier> tiersAsc) {
        Tier result = Tier.BRONZE;
        for (LoyaltyTier t : tiersAsc) {
            if (score.compareTo(BigDecimal.valueOf(t.getMinScore())) >= 0) {
                result = t.getTier();
            }
        }
        return result;
    }
}
