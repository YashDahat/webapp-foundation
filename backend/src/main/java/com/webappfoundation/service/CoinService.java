package com.webappfoundation.service;

import com.webappfoundation.model.CoinLedgerEntry;
import com.webappfoundation.model.CoinStatus;
import com.webappfoundation.model.LoyaltyProfile;
import com.webappfoundation.model.LoyaltySettings;
import com.webappfoundation.model.LoyaltyTier;
import com.webappfoundation.repository.CoinLedgerEntryRepository;
import com.webappfoundation.repository.LoyaltyProfileRepository;
import com.webappfoundation.repository.LoyaltyTierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The reward-coin ledger: earn (pending until the return window), nightly confirm + expiry, redeem at
 * checkout (atomic, never overdraws), and full reversal on return/cancel. The spendable balance is the
 * denormalised {@code LoyaltyProfile.coinBalance} (= Σ confirmed, unexpired entries), maintained here.
 * 1 coin = {@code LoyaltySettings.coinValue} rupees.
 */
@Service
public class CoinService {

    private static final Logger log = LoggerFactory.getLogger(CoinService.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final CoinLedgerEntryRepository coinRepository;
    private final LoyaltyProfileRepository profileRepository;
    private final LoyaltyTierRepository tierRepository;
    private final LoyaltyProfileService profileService;
    private final LoyaltySettingsService settingsService;

    public CoinService(CoinLedgerEntryRepository coinRepository, LoyaltyProfileRepository profileRepository,
                       LoyaltyTierRepository tierRepository, LoyaltyProfileService profileService,
                       LoyaltySettingsService settingsService) {
        this.coinRepository = coinRepository;
        this.profileRepository = profileRepository;
        this.tierRepository = tierRepository;
        this.profileService = profileService;
        this.settingsService = settingsService;
    }

    /** Spendable (confirmed, unexpired) coin balance. */
    public BigDecimal spendable(Integer userId) {
        return profileRepository.findById(userId).map(LoyaltyProfile::getCoinBalance).orElse(BigDecimal.ZERO);
    }

    /** The customer's coin ledger, newest first, for the account page history. */
    public List<com.webappfoundation.dto.CoinLedgerDto> history(Integer userId) {
        if (userId == null) return List.of();
        return coinRepository.findByUserIdOrderByEarnedAtDesc(userId).stream()
                .map(e -> com.webappfoundation.dto.CoinLedgerDto.builder()
                        .id(e.getId()).coins(e.getCoins()).reason(e.getReason())
                        .status(e.getStatus()).earnedAt(e.getEarnedAt()).expiresAt(e.getExpiresAt())
                        .build())
                .toList();
    }

    /**
     * Earn coins for a paid order: {@code floor(orderTotal/100 × tier.coinsPer100)}, written PENDING (they
     * become spendable after the return window). Idempotent per order — skips if an earn entry already exists.
     */
    @Transactional
    public BigDecimal earnForOrder(Integer userId, BigDecimal orderTotal, Long orderId) {
        if (userId == null || orderTotal == null || orderId == null) return BigDecimal.ZERO;
        boolean alreadyEarned = coinRepository.findByOrderId(orderId).stream()
                .anyMatch(e -> "purchase".equals(e.getReason()) && e.getCoins().signum() > 0);
        if (alreadyEarned) return BigDecimal.ZERO;

        LoyaltyProfile p = profileService.getOrCreate(userId);
        BigDecimal coinsPer100 = tierRepository.findByTier(p.getTier())
                .map(LoyaltyTier::getCoinsPer100).orElse(BigDecimal.ZERO);
        BigDecimal coins = orderTotal.divide(HUNDRED, 4, RoundingMode.DOWN).multiply(coinsPer100)
                .setScale(0, RoundingMode.DOWN);
        if (coins.signum() <= 0) return BigDecimal.ZERO;

        Instant now = Instant.now();
        coinRepository.save(CoinLedgerEntry.builder()
                .userId(userId).coins(coins).reason("purchase").orderId(orderId)
                .status(CoinStatus.PENDING).earnedAt(now)
                .expiresAt(now.plus(30L * settingsService.get().getCoinExpiryMonths(), ChronoUnit.DAYS))
                .build());
        return coins;
    }

    /**
     * Redeem the coin value applied to an order (from its loyalty.coins adjustment) — atomic, idempotent.
     * {@code rupeesOff} is the positive cash value taken off. Returns the coins actually spent (0 if skipped).
     */
    @Transactional
    public BigDecimal redeemForOrder(Integer userId, BigDecimal rupeesOff, Long orderId) {
        if (userId == null || rupeesOff == null || rupeesOff.signum() <= 0 || orderId == null) return BigDecimal.ZERO;
        boolean alreadyRedeemed = coinRepository.findByOrderId(orderId).stream()
                .anyMatch(e -> "redeem".equals(e.getReason()));
        if (alreadyRedeemed) return BigDecimal.ZERO;

        BigDecimal coinValue = settingsService.get().getCoinValue();
        BigDecimal coins = rupeesOff.divide(coinValue, 0, RoundingMode.HALF_UP);
        if (coins.signum() <= 0) return BigDecimal.ZERO;

        if (profileRepository.adjustCoinBalance(userId, coins.negate()) == 0) {
            log.warn("[loyalty] coin redemption skipped for order {} — insufficient balance for {} coins", orderId, coins);
            return BigDecimal.ZERO;
        }
        coinRepository.save(CoinLedgerEntry.builder()
                .userId(userId).coins(coins.negate()).reason("redeem").orderId(orderId)
                .status(CoinStatus.CONFIRMED).earnedAt(Instant.now()).build());
        return coins;
    }

    /** Reverse every coin effect of an order on return/cancel: earned coins pulled back, redeemed coins refunded. */
    @Transactional
    public void reverseForOrder(Long orderId) {
        for (CoinLedgerEntry e : coinRepository.findByOrderId(orderId)) {
            if (e.getStatus() == CoinStatus.REVERSED || e.getStatus() == CoinStatus.EXPIRED) continue;
            if (e.getCoins().signum() > 0 && e.getStatus() == CoinStatus.CONFIRMED) {
                profileRepository.adjustCoinBalance(e.getUserId(), e.getCoins().negate());   // pull back confirmed earnings
            } else if (e.getCoins().signum() < 0) {
                profileRepository.adjustCoinBalance(e.getUserId(), e.getCoins().negate());   // refund redeemed coins
            }
            e.setStatus(CoinStatus.REVERSED);
            coinRepository.save(e);
        }
    }

    /**
     * Credit coins for an engagement action (review, feedback, suggestion, referral …) whose scoring rule has
     * {@code earnsCoins}. Written CONFIRMED (spendable at once) with the normal expiry. Returns coins credited.
     */
    @Transactional
    public BigDecimal earnForAction(Integer userId, BigDecimal coins, String reason) {
        if (userId == null || coins == null) return BigDecimal.ZERO;
        BigDecimal whole = coins.setScale(0, RoundingMode.DOWN);
        if (whole.signum() <= 0) return BigDecimal.ZERO;
        profileService.getOrCreate(userId);
        Instant now = Instant.now();
        coinRepository.save(CoinLedgerEntry.builder()
                .userId(userId).coins(whole).reason(reason)
                .status(CoinStatus.CONFIRMED).earnedAt(now)
                .expiresAt(now.plus(30L * settingsService.get().getCoinExpiryMonths(), ChronoUnit.DAYS))
                .build());
        profileRepository.adjustCoinBalance(userId, whole);
        return whole;
    }

    // ── Nightly maintenance ──

    /** Confirm pending earnings whose return window has closed; they become spendable. Returns count confirmed. */
    @Transactional
    public int confirmDueEarnings() {
        LoyaltySettings settings = settingsService.get();
        Instant cutoff = Instant.now().minus(settings.getReturnWindowDays(), ChronoUnit.DAYS);
        List<CoinLedgerEntry> due = coinRepository.findByStatusAndEarnedAtBefore(CoinStatus.PENDING, cutoff);
        for (CoinLedgerEntry e : due) {
            e.setStatus(CoinStatus.CONFIRMED);
            coinRepository.save(e);
            profileRepository.adjustCoinBalance(e.getUserId(), e.getCoins());
        }
        return due.size();
    }

    /** Expire confirmed coins past their lifetime; they leave the spendable balance. Returns count expired. */
    @Transactional
    public int expireDueCoins() {
        List<CoinLedgerEntry> due = coinRepository.findByStatusAndExpiresAtBefore(CoinStatus.CONFIRMED, Instant.now());
        for (CoinLedgerEntry e : due) {
            e.setStatus(CoinStatus.EXPIRED);
            coinRepository.save(e);
            profileRepository.adjustCoinBalance(e.getUserId(), e.getCoins().negate());
        }
        return due.size();
    }
}
