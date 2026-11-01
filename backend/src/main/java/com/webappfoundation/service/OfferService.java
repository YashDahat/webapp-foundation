package com.webappfoundation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webappfoundation.dto.OfferValidationResult;
import com.webappfoundation.model.LoyaltyProfile;
import com.webappfoundation.model.LoyaltySettings;
import com.webappfoundation.model.LoyaltyTriggerConfig;
import com.webappfoundation.model.Offer;
import com.webappfoundation.model.OfferStatus;
import com.webappfoundation.model.OfferTrigger;
import com.webappfoundation.model.OfferType;
import com.webappfoundation.model.Order;
import com.webappfoundation.model.OrderAdjustment;
import com.webappfoundation.model.Redemption;
import com.webappfoundation.model.Tier;
import com.webappfoundation.model.User;
import com.webappfoundation.repository.LoyaltyTriggerConfigRepository;
import com.webappfoundation.repository.OfferRepository;
import com.webappfoundation.repository.RedemptionRepository;
import com.webappfoundation.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * The discount offer engine (spec: "cash discounts are the last resort, not the default"). On a trigger it
 * runs holdout + guards → cheapest rung first → budget check → sizes a personal code
 * {@code d = min(base·Ttier·Rchurn·Ppace, Dmax)} with a friendly min-spend, issues it (single-use, short
 * expiry) and emails it. At checkout it validates a code and, on the paid stage, commits the redemption
 * (atomic usedCount, Redemption row, budget spend). Everything here is gated by the master
 * {@code discountsEnabled} kill switch; a customer in the holdout never receives a code.
 */
@Service
public class OfferService {

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);
    private static final String SAFE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BigDecimal MAX_PCT = new BigDecimal("20");   // absolute ceiling (Dmax)
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final OfferRepository offerRepository;
    private final RedemptionRepository redemptionRepository;
    private final LoyaltyTriggerConfigRepository triggerRepository;
    private final LoyaltyProfileService profileService;
    private final LoyaltySettingsService settingsService;
    private final BudgetPacingService budgetPacing;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OfferService(OfferRepository offerRepository, RedemptionRepository redemptionRepository,
                        LoyaltyTriggerConfigRepository triggerRepository, LoyaltyProfileService profileService,
                        LoyaltySettingsService settingsService, BudgetPacingService budgetPacing,
                        UserRepository userRepository, EmailService emailService, AuditService auditService) {
        this.offerRepository = offerRepository;
        this.redemptionRepository = redemptionRepository;
        this.triggerRepository = triggerRepository;
        this.profileService = profileService;
        this.settingsService = settingsService;
        this.budgetPacing = budgetPacing;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.auditService = auditService;
    }

    // ── Issue ──

    /** Decide and (if everything passes) issue a personal offer for a trigger. Returns the offer or null. */
    @Transactional
    public Offer decideOffer(Integer userId, OfferTrigger trigger, BigDecimal cartSubtotal) {
        try {
            LoyaltySettings settings = settingsService.get();
            if (!settings.isDiscountsEnabled() || userId == null) return null;     // master kill switch

            LoyaltyTriggerConfig cfg = triggerRepository.findByTrigger(trigger).orElse(null);
            if (cfg == null || !cfg.isEnabled()) return null;

            LoyaltyProfile profile = profileService.getOrCreate(userId);
            if (!passesGuards(profile, settings, trigger)) return null;

            JsonNode rung = cheapestRung(cfg);
            if (rung == null) return null;
            OfferType type = OfferType.valueOf(rung.path("type").asText("PCT").toUpperCase());
            Instant now = Instant.now();
            Instant expiresAt = now.plus(cfg.getExpiryDays(), ChronoUnit.DAYS);

            Offer.OfferBuilder b = Offer.builder()
                    .code(generateCode(prefix(trigger))).userId(userId).trigger(trigger).rung(1)
                    .maxUses(1).usedCount(0).status(OfferStatus.ACTIVE)
                    .startsAt(now).expiresAt(expiresAt).createdAt(now);

            if (type == OfferType.PCT) {
                if (budgetPacing.budgetExhausted()) return null;                   // budget used up → no cash offer
                BigDecimal base = decimal(rung, "value", new BigDecimal("10"));
                BigDecimal pct = base.multiply(tierFactor(profile.getTier()))
                        .multiply(churnFactor(profile)).multiply(budgetPacing.paceFactor());
                pct = pct.min(MAX_PCT).setScale(0, RoundingMode.DOWN);
                if (decimal(rung, "maxDiscount", null) != null) b.maxDiscount(decimal(rung, "maxDiscount", null));
                if (pct.signum() <= 0) return null;
                b.type(OfferType.PCT).value(pct).minSpend(friendlyMinSpend(profile.getAvgOrderValue()));
            } else if (type == OfferType.FLAT) {
                if (budgetPacing.budgetExhausted()) return null;
                b.type(OfferType.FLAT).value(decimal(rung, "value", new BigDecimal("100")))
                        .minSpend(friendlyMinSpend(profile.getAvgOrderValue()));
            } else {
                // Non-cash reward (FREE_SHIP / GIFT / COINS) — no budget draw.
                b.type(type).value(decimal(rung, "value", null));
            }

            Offer offer = offerRepository.save(b.build());
            emailOffer(userId, offer);
            auditService.record(AuditActions.LOYALTY_OFFER_ISSUED, AuditActions.SUCCESS, "LOYALTY_OFFER",
                    String.valueOf(offer.getId()), "trigger=" + trigger + " type=" + offer.getType() + " code=" + offer.getCode());
            return offer;
        } catch (Exception e) {
            log.warn("[loyalty] decideOffer {} failed for user {}: {}", trigger, userId, e.getMessage());
            return null;
        }
    }

    /** Nightly time-triggers: win-back (gone quiet) and birthday (this month, profile complete). */
    @Transactional
    public void checkTimeTriggers(LoyaltyProfile p) {
        if (p == null || p.getUserId() == null) return;
        Instant now = Instant.now();
        // Win-back: no order for 1.5× the usual gap (min 45 days).
        if (p.getLastOrderAt() != null && p.getUsualOrderGapDays() != null) {
            long quietFor = ChronoUnit.DAYS.between(p.getLastOrderAt(), now);
            long threshold = Math.max(45, Math.round(p.getUsualOrderGapDays() * 1.5));
            if (quietFor >= threshold) {
                decideOffer(p.getUserId(), OfferTrigger.WIN_BACK, null);
            }
        }
        // Birthday: in the birthday month with a completed profile.
        if (p.getBirthday() != null && p.getProfileCompletedAt() != null
                && p.getBirthday().getMonthValue() == LocalDate.now().getMonthValue()) {
            decideOffer(p.getUserId(), OfferTrigger.BIRTHDAY, null);
        }
    }

    // ── Validate (checkout preview + the discount connector) ──

    public OfferValidationResult validate(Integer userId, String code, BigDecimal subtotal) {
        if (code == null || code.isBlank()) return OfferValidationResult.invalid(code, "Enter a code.");
        if (!settingsService.get().isDiscountsEnabled()) return OfferValidationResult.invalid(code, "Discounts are currently unavailable.");
        Offer offer = offerRepository.findByCode(code.trim().toUpperCase()).orElse(null);
        if (offer == null) return OfferValidationResult.invalid(code, "That code isn’t valid.");
        Instant now = Instant.now();
        if (offer.getStatus() != OfferStatus.ACTIVE) return OfferValidationResult.invalid(code, "This code is no longer active.");
        if (offer.getExpiresAt() != null && now.isAfter(offer.getExpiresAt())) return OfferValidationResult.invalid(code, "This code has expired.");
        if (offer.getStartsAt() != null && now.isBefore(offer.getStartsAt())) return OfferValidationResult.invalid(code, "This code isn’t active yet.");
        if (offer.getUserId() != null && !offer.getUserId().equals(userId)) return OfferValidationResult.invalid(code, "This code belongs to another account.");
        if (offer.getUsedCount() >= offer.getMaxUses()) return OfferValidationResult.invalid(code, "This code has already been used.");
        BigDecimal sub = subtotal == null ? BigDecimal.ZERO : subtotal;
        if (offer.getMinSpend() != null && sub.compareTo(offer.getMinSpend()) < 0) {
            return OfferValidationResult.invalid(code, "Spend ₹" + offer.getMinSpend().toPlainString() + " to use this code.");
        }
        BigDecimal amount = discountAmountFor(offer, sub);
        if (amount.signum() <= 0) return OfferValidationResult.invalid(code, "This code can’t be applied to this cart.");
        return OfferValidationResult.builder().valid(true).code(offer.getCode()).amount(amount)
                .message("₹" + amount.toPlainString() + " off applied.").build();
    }

    /** Cash value a code takes off a cart subtotal (PCT capped by maxDiscount; FLAT flat; non-cash → 0). */
    public BigDecimal discountAmountFor(Offer offer, BigDecimal subtotal) {
        BigDecimal amount = switch (offer.getType()) {
            case PCT -> subtotal.multiply(offer.getValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            case FLAT -> offer.getValue() == null ? BigDecimal.ZERO : offer.getValue();
            default -> BigDecimal.ZERO;         // FREE_SHIP/GIFT/COINS aren't a cart cash discount here
        };
        if (offer.getMaxDiscount() != null && amount.compareTo(offer.getMaxDiscount()) > 0) {
            amount = offer.getMaxDiscount();
        }
        return amount.min(subtotal).max(BigDecimal.ZERO);
    }

    // ── Commit (paid stage) ──

    /** Commit a code redemption on an order: atomic usedCount, a Redemption row, and budget spend. Idempotent. */
    @Transactional
    public void commitRedemption(Order order, OrderAdjustment adj) {
        if (adj == null || adj.getCode() == null || order == null) return;
        if (redemptionRepository.existsByOrderId(order.getId())) return;        // already committed
        Offer offer = offerRepository.findByCode(adj.getCode().trim().toUpperCase()).orElse(null);
        if (offer == null) return;
        if (offerRepository.claimUse(offer.getId()) == 0) {
            log.warn("[loyalty] code {} could not be claimed for order {} (exhausted/concurrent)", adj.getCode(), order.getId());
            return;
        }
        BigDecimal discountValue = adj.getAmount() == null ? BigDecimal.ZERO : adj.getAmount().abs();
        redemptionRepository.save(Redemption.builder()
                .code(offer.getCode()).orderId(order.getId()).userId(order.getUserId())
                .discountValue(discountValue).createdAt(Instant.now()).build());
        budgetPacing.recordSpend(discountValue);
        // Mark the offer used when its uses are exhausted (re-read the claimed count).
        offerRepository.findById(offer.getId()).ifPresent(o -> {
            if (o.getUsedCount() >= o.getMaxUses()) { o.setStatus(OfferStatus.USED); offerRepository.save(o); }
        });
        auditService.record(AuditActions.LOYALTY_CODE_REDEEMED, AuditActions.SUCCESS, "LOYALTY_OFFER",
                String.valueOf(offer.getId()), "code=" + offer.getCode() + " order=" + order.getId() + " value=" + discountValue);
    }

    // ── Guards ──

    private boolean passesGuards(LoyaltyProfile p, LoyaltySettings s, OfferTrigger trigger) {
        if (p.isInHoldout()) return false;                                      // holdout never gets codes
        if (p.getReturnRate() != null
                && p.getReturnRate().compareTo(BigDecimal.valueOf(s.getReturnRateBlockPct()).movePointLeft(2)) > 0) {
            return false;                                                       // return rate above the block threshold
        }
        List<Offer> recent = offerRepository.findByUserIdOrderByCreatedAtDesc(p.getUserId());
        // One active personal code at a time.
        if (recent.stream().anyMatch(o -> o.getStatus() == OfferStatus.ACTIVE)) return false;
        // No new code within the cooldown window of the last one.
        Instant cooldownCut = Instant.now().minus(s.getNewCodeCooldownDays(), ChronoUnit.DAYS);
        if (recent.stream().anyMatch(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(cooldownCut))) return false;
        // Don't re-fire the same trigger within its own window.
        if (recent.stream().anyMatch(o -> o.getTrigger() == trigger && o.getCreatedAt() != null
                && o.getCreatedAt().isAfter(Instant.now().minus(30, ChronoUnit.DAYS)))) return false;
        return true;
    }

    // ── Sizing helpers ──

    private BigDecimal tierFactor(Tier tier) {
        return switch (tier == null ? Tier.BRONZE : tier) {
            case BRONZE -> BigDecimal.ONE;
            case SILVER -> new BigDecimal("1.1");
            case GOLD -> new BigDecimal("1.2");
            case PLATINUM -> new BigDecimal("1.3");
        };
    }

    /** 1.0 normally, up to 1.5 when the customer is well past their usual order gap (churn risk). */
    private BigDecimal churnFactor(LoyaltyProfile p) {
        if (p.getLastOrderAt() == null || p.getUsualOrderGapDays() == null || p.getUsualOrderGapDays() <= 0) {
            return BigDecimal.ONE;
        }
        long days = ChronoUnit.DAYS.between(p.getLastOrderAt(), Instant.now());
        return days > 2L * p.getUsualOrderGapDays() ? new BigDecimal("1.5") : BigDecimal.ONE;
    }

    /** Friendly min-spend ≈ 1.25 × AOV, rounded up to the nearest ₹100 (min ₹500). */
    private BigDecimal friendlyMinSpend(BigDecimal aov) {
        BigDecimal base = (aov == null ? BigDecimal.ZERO : aov).multiply(new BigDecimal("1.25"));
        BigDecimal rounded = base.divide(HUNDRED, 0, RoundingMode.UP).multiply(HUNDRED);
        return rounded.max(new BigDecimal("500"));
    }

    private JsonNode cheapestRung(LoyaltyTriggerConfig cfg) {
        try {
            if (cfg.getRungsJson() == null || cfg.getRungsJson().isBlank()) return null;
            JsonNode arr = objectMapper.readTree(cfg.getRungsJson());
            return arr.isArray() && arr.size() > 0 ? arr.get(0) : null;         // rung 1 = cheapest
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal decimal(JsonNode node, String field, BigDecimal fallback) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? fallback : new BigDecimal(v.asText());
    }

    private String prefix(OfferTrigger t) {
        return switch (t) {
            case WELCOME -> "WEL"; case WIN_BACK -> "WB"; case BIRTHDAY -> "BDAY"; case MILESTONE -> "MILE";
            case TIER_UP -> "TIER"; case SLOW_STOCK -> "STOCK"; case SURPRISE -> "SURP"; case FESTIVE -> "FEST";
            case ABANDONED_CART -> "CART"; case MANUAL -> "MAN";
        };
    }

    private String generateCode(String prefix) {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(prefix).append('-');
            for (int i = 0; i < 6; i++) sb.append(SAFE_CHARS.charAt(RANDOM.nextInt(SAFE_CHARS.length())));
            String code = sb.toString();
            if (!offerRepository.existsByCode(code)) return code;
        }
        return prefix + "-" + Instant.now().toEpochMilli();
    }

    private void emailOffer(Integer userId, Offer offer) {
        try {
            User u = userRepository.findById(userId).orElse(null);
            if (u == null || u.getEmail() == null) return;
            String reward = offer.getType() == OfferType.PCT ? offer.getValue() + "% off"
                    : offer.getType() == OfferType.FLAT ? "₹" + offer.getValue() + " off"
                    : offer.getType().name().replace('_', ' ').toLowerCase();
            String html = "<p>Here’s a little something for you: <strong>" + reward + "</strong>.</p>"
                    + "<p>Use code <strong>" + offer.getCode() + "</strong> at checkout"
                    + (offer.getMinSpend() != null ? " on orders over ₹" + offer.getMinSpend().toPlainString() : "")
                    + ". Valid until " + LocalDate.ofInstant(offer.getExpiresAt(), ZoneOffset.UTC) + ".</p>";
            emailService.sendHtml(u.getEmail(), "A reward just for you", html);
        } catch (Exception e) {
            log.debug("[loyalty] offer email skipped: {}", e.getMessage());
        }
    }
}
