package com.webappfoundation.service;

import com.webappfoundation.dto.LoyaltyMeDto;
import com.webappfoundation.dto.LoyaltyProfileDto;
import com.webappfoundation.model.LoyaltyProfile;
import com.webappfoundation.model.LoyaltyTier;
import com.webappfoundation.model.Tier;
import com.webappfoundation.model.User;
import com.webappfoundation.repository.LoyaltyProfileRepository;
import com.webappfoundation.repository.LoyaltyTierRepository;
import com.webappfoundation.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loyalty-profile access. Admins read the customer loyalty grid here (score/tier/coins + stats, with the
 * user's email/name resolved for display). Also exposes {@link #getOrCreate} so the Phase 1+ engine can
 * lazily materialise a profile (with a one-time holdout assignment) the first time it touches a user.
 */
@Service
public class LoyaltyProfileService {

    private final LoyaltyProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final LoyaltySettingsService settingsService;
    private final LoyaltyTierRepository tierRepository;
    private final LoyaltyScoreService scoreService;

    public LoyaltyProfileService(LoyaltyProfileRepository profileRepository, UserRepository userRepository,
                                 LoyaltySettingsService settingsService, LoyaltyTierRepository tierRepository,
                                 LoyaltyScoreService scoreService) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.tierRepository = tierRepository;
        this.scoreService = scoreService;
    }

    public List<LoyaltyProfileDto> adminList() {
        List<LoyaltyProfile> profiles = profileRepository.findAll();
        Map<Integer, User> users = userRepository.findAllById(
                        profiles.stream().map(LoyaltyProfile::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return profiles.stream()
                .map(p -> toDto(p, users.get(p.getUserId())))
                .sorted(Comparator.comparing(LoyaltyProfileDto::getScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** Materialise (or fetch) a user's loyalty profile, assigning the fixed holdout bucket exactly once. */
    @Transactional
    public LoyaltyProfile getOrCreate(Integer userId) {
        return profileRepository.findById(userId).orElseGet(() -> {
            Instant now = Instant.now();
            boolean holdout = Math.random() * 100 < settingsService.get().getHoldoutPct();
            return profileRepository.save(LoyaltyProfile.builder()
                    .userId(userId)
                    .score(BigDecimal.ZERO)
                    .coinBalance(BigDecimal.ZERO)
                    .avgOrderValue(BigDecimal.ZERO)
                    .returnRate(BigDecimal.ZERO)
                    .inHoldout(holdout)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        });
    }

    /** The caller's own loyalty snapshot (recomputed live so the account page is fresh). */
    @Transactional
    public LoyaltyMeDto me(Integer userId) {
        getOrCreate(userId);
        LoyaltyProfile p = scoreService.recompute(userId);          // persists the fresh score/tier
        return toMeDto(p);
    }

    @Transactional
    public LoyaltyMeDto setTrackingConsent(Integer userId, boolean consent) {
        LoyaltyProfile p = getOrCreate(userId);
        p.setTrackingConsentAt(consent ? Instant.now() : null);
        p.setUpdatedAt(Instant.now());
        profileRepository.save(p);
        return me(userId);
    }

    private LoyaltyMeDto toMeDto(LoyaltyProfile p) {
        List<LoyaltyTier> tiersAsc = tierRepository.findAllByOrderByMinScoreAsc();
        LoyaltyTier currentCfg = tiersAsc.stream().filter(t -> t.getTier() == p.getTier()).findFirst().orElse(null);
        LoyaltyTier next = tiersAsc.stream()
                .filter(t -> currentCfg == null || t.getMinScore() > currentCfg.getMinScore())
                .min(Comparator.comparingInt(LoyaltyTier::getMinScore))
                .orElse(null);

        int pointsToNext = 0;
        boolean near = false;
        if (next != null) {
            double remaining = next.getMinScore() - p.getScore().doubleValue();
            pointsToNext = (int) Math.max(0, Math.ceil(remaining));
            near = p.getScore().doubleValue() >= 0.9 * next.getMinScore();
        }
        return LoyaltyMeDto.builder()
                .userId(p.getUserId())
                .tier(p.getTier())
                .tierName(currentCfg == null ? (p.getTier() == null ? null : p.getTier().name()) : currentCfg.getName())
                .score(p.getScore())
                .coinBalance(p.getCoinBalance())
                .nextTier(next == null ? null : next.getTier())
                .pointsToNextTier(pointsToNext)
                .nearNextTier(near)
                .inHoldout(p.isInHoldout())
                .trackingConsent(p.getTrackingConsentAt() != null)
                .discountsEnabled(settingsService.get().isDiscountsEnabled())
                .build();
    }

    private LoyaltyProfileDto toDto(LoyaltyProfile p, User u) {
        String name = u == null ? null
                : ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                   + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return LoyaltyProfileDto.builder()
                .userId(p.getUserId())
                .userEmail(u == null ? null : u.getEmail())
                .userName(name == null || name.isEmpty() ? null : name)
                .tier(p.getTier())
                .score(p.getScore())
                .coinBalance(p.getCoinBalance())
                .avgOrderValue(p.getAvgOrderValue())
                .usualOrderGapDays(p.getUsualOrderGapDays())
                .lastOrderAt(p.getLastOrderAt())
                .returnRate(p.getReturnRate())
                .inHoldout(p.isInHoldout())
                .birthday(p.getBirthday())
                .scoreUpdatedAt(p.getScoreUpdatedAt())
                .build();
    }
}
