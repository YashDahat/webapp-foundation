package com.webappfoundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webappfoundation.model.LoyaltyEvent;
import com.webappfoundation.model.LoyaltyEventRule;
import com.webappfoundation.model.LoyaltyEventStatus;
import com.webappfoundation.model.LoyaltyEventType;
import com.webappfoundation.model.LoyaltySettings;
import com.webappfoundation.model.OfferTrigger;
import com.webappfoundation.repository.LoyaltyEventRepository;
import com.webappfoundation.repository.LoyaltyEventRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Records scored customer actions ({@code LoyaltyEvent}s) in real time, applying each
 * {@code LoyaltyEventRule}: per-type daily occurrence caps, the combined non-purchase daily/monthly points
 * caps, and once-only rules. Purchase points are written {@code PENDING} and confirm after the return
 * window (the nightly job flips them); everything else is {@code CONFIRMED} on write.
 *
 * <p>Like {@link AuditService}, recording is best-effort and NEVER throws to its caller — a loyalty hook
 * must never break the business flow (review/checkout) that triggered it. Coin accrual for earns-coins
 * events is layered on in Phase 2.
 */
@Service
public class LoyaltyEventService {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyEventService.class);
    private static final BigDecimal THOUSAND = new BigDecimal("1000");

    private final LoyaltyEventRepository eventRepository;
    private final LoyaltyEventRuleRepository ruleRepository;
    private final LoyaltyProfileService profileService;
    private final LoyaltySettingsService settingsService;
    private final OfferService offerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LoyaltyEventService(LoyaltyEventRepository eventRepository, LoyaltyEventRuleRepository ruleRepository,
                               LoyaltyProfileService profileService, LoyaltySettingsService settingsService,
                               OfferService offerService) {
        this.eventRepository = eventRepository;
        this.ruleRepository = ruleRepository;
        this.profileService = profileService;
        this.settingsService = settingsService;
        this.offerService = offerService;
    }

    /** Materialise the user's profile (assigning holdout once) and award the one-time SIGNUP points. */
    @Transactional
    public void ensureBootstrap(Integer userId) {
        if (userId == null) return;
        try {
            profileService.getOrCreate(userId);
            if (!eventRepository.existsByUserIdAndType(userId, LoyaltyEventType.SIGNUP)) {
                recordInternal(userId, LoyaltyEventType.SIGNUP, null, null);
                offerService.decideOffer(userId, OfferTrigger.WELCOME, null);    // first-ever bootstrap → welcome offer
            }
        } catch (Exception e) {
            log.warn("[loyalty] bootstrap failed for user {}: {}", userId, e.getMessage());
        }
    }

    /** Record a scored action. No-op for guests; resilient (never throws to the caller). */
    @Transactional
    public void record(Integer userId, LoyaltyEventType type, Long orderId, Map<String, Object> meta) {
        if (userId == null || type == null) return;
        try {
            if (type != LoyaltyEventType.SIGNUP) {
                ensureBootstrap(userId);
            }
            recordInternal(userId, type, orderId, meta);
        } catch (Exception e) {
            log.warn("[loyalty] record {} failed for user {}: {}", type, userId, e.getMessage());
        }
    }

    /**
     * Convenience for the frontend activity beacon (view / add-to-cart / wishlist / daily visit / share).
     * Behavioural beacons require DPDP tracking consent — without it they are silently ignored.
     */
    @Transactional
    public void recordActivity(Integer userId, LoyaltyEventType type, String itemType, Long itemId) {
        if (userId == null) return;
        if (profileService.getOrCreate(userId).getTrackingConsentAt() == null) return;
        Map<String, Object> meta = (itemType == null && itemId == null) ? null
                : Map.of("itemType", itemType == null ? "" : itemType, "itemId", itemId == null ? 0L : itemId);
        record(userId, type, null, meta);
    }

    /** Reverse an order's purchase score points on return/cancel (pending or confirmed → reversed). */
    @Transactional
    public void reversePurchase(Integer userId, Long orderId) {
        if (orderId == null) return;
        try {
            for (LoyaltyEvent e : eventRepository.findByOrderId(orderId)) {
                if (e.getType() == LoyaltyEventType.PURCHASE && e.getStatus() != LoyaltyEventStatus.REVERSED) {
                    e.setStatus(LoyaltyEventStatus.REVERSED);
                    eventRepository.save(e);
                }
            }
        } catch (Exception ex) {
            log.warn("[loyalty] reversePurchase failed for order {}: {}", orderId, ex.getMessage());
        }
    }

    // ── Core ──

    private void recordInternal(Integer userId, LoyaltyEventType type, Long orderId, Map<String, Object> meta) {
        LoyaltyEventRule rule = ruleRepository.findByEventType(type).orElse(null);
        if (rule == null) return;                                   // no configured rule → no points
        if (rule.isOnceOnly() && eventRepository.existsByUserIdAndType(userId, type)) return;

        LoyaltySettings settings = settingsService.get();
        Instant now = Instant.now();
        BigDecimal points;
        LoyaltyEventStatus status;
        Instant confirmAt = null;

        if (type == LoyaltyEventType.PURCHASE) {
            BigDecimal amountPaid = metaDecimal(meta, "amountPaid");
            points = rule.getPoints().multiply(amountPaid).divide(THOUSAND, 2, RoundingMode.HALF_UP);
            if (points.signum() <= 0) return;
            status = LoyaltyEventStatus.PENDING;                    // confirms once the return window closes
            confirmAt = now.plus(settings.getReturnWindowDays(), ChronoUnit.DAYS);
        } else {
            // "Farmable" repeatable actions carry a per-day cap (views/cart/wishlist/visit/share). Only these
            // are subject to the per-type cap AND the combined daily/monthly caps; one-time (signup/profile)
            // and high-value (review/referral) events are not throttled by the anti-farming caps.
            boolean farmable = rule.getPerDayCap() != null;
            points = rule.getPoints();
            if (farmable) {
                if (eventRepository.countByUserIdAndTypeAndCreatedAtAfter(userId, type, startOfDay(now)) >= rule.getPerDayCap()) {
                    return;                                         // per-type daily occurrence cap hit
                }
                List<LoyaltyEventType> farmableTypes = farmableTypes();
                if (!farmableTypes.isEmpty()) {
                    BigDecimal todaySum = eventRepository.sumPointsByTypesSince(userId, farmableTypes, startOfDay(now));
                    BigDecimal monthSum = eventRepository.sumPointsByTypesSince(userId, farmableTypes, startOfMonth(now));
                    points = points.min(BigDecimal.valueOf(settings.getDailyCap()).subtract(todaySum))
                                   .min(BigDecimal.valueOf(settings.getMonthlyCap()).subtract(monthSum));
                    if (points.signum() <= 0) return;               // combined caps exhausted
                }
            }
            status = LoyaltyEventStatus.CONFIRMED;
        }

        eventRepository.save(LoyaltyEvent.builder()
                .userId(userId).type(type).points(points).status(status)
                .orderId(orderId).metaJson(toJson(meta)).createdAt(now).confirmAt(confirmAt)
                .build());
    }

    /** The farmable repeatable event types — those configured with a per-day cap — that share the combined caps. */
    private List<LoyaltyEventType> farmableTypes() {
        return ruleRepository.findAll().stream()
                .filter(r -> r.getPerDayCap() != null)
                .map(LoyaltyEventRule::getEventType)
                .toList();
    }

    private Instant startOfDay(Instant now) {
        return now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private Instant startOfMonth(Instant now) {
        return now.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private BigDecimal metaDecimal(Map<String, Object> meta, String key) {
        if (meta == null || meta.get(key) == null) return BigDecimal.ZERO;
        try { return new BigDecimal(meta.get(key).toString()); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private String toJson(Map<String, Object> meta) {
        if (meta == null || meta.isEmpty()) return null;
        try { return objectMapper.writeValueAsString(meta); } catch (Exception e) { return null; }
    }
}
