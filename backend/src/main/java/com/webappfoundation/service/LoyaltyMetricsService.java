package com.webappfoundation.service;

import com.webappfoundation.dto.LoyaltyMetricsDto;
import com.webappfoundation.dto.LoyaltyMetricsDto.BudgetMetrics;
import com.webappfoundation.dto.LoyaltyMetricsDto.HoldoutComparison;
import com.webappfoundation.dto.LoyaltyMetricsDto.TierCount;
import com.webappfoundation.dto.LoyaltyMetricsDto.TriggerStat;
import com.webappfoundation.model.DiscountBudget;
import com.webappfoundation.model.LoyaltyProfile;
import com.webappfoundation.model.LoyaltySettings;
import com.webappfoundation.model.Offer;
import com.webappfoundation.model.OfferTrigger;
import com.webappfoundation.model.Redemption;
import com.webappfoundation.model.Tier;
import com.webappfoundation.repository.DiscountBudgetRepository;
import com.webappfoundation.repository.LoyaltyProfileRepository;
import com.webappfoundation.repository.OfferRepository;
import com.webappfoundation.repository.OrderRepository;
import com.webappfoundation.repository.RedemptionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the admin loyalty metrics dashboard aggregates: budget vs discount spend, tier mix, coin
 * liability, redemption by trigger, and the all-important members-vs-holdout comparison (repeat rate +
 * incremental revenue). Aggregation is at SMB scale (a handful of admin reads); heavy per-user stats come
 * from grouped queries, not row-by-row loads.
 */
@Service
public class LoyaltyMetricsService {

    private final LoyaltyProfileRepository profileRepository;
    private final OfferRepository offerRepository;
    private final RedemptionRepository redemptionRepository;
    private final OrderRepository orderRepository;
    private final DiscountBudgetRepository budgetRepository;
    private final LoyaltySettingsService settingsService;

    public LoyaltyMetricsService(LoyaltyProfileRepository profileRepository, OfferRepository offerRepository,
                                 RedemptionRepository redemptionRepository, OrderRepository orderRepository,
                                 DiscountBudgetRepository budgetRepository, LoyaltySettingsService settingsService) {
        this.profileRepository = profileRepository;
        this.offerRepository = offerRepository;
        this.redemptionRepository = redemptionRepository;
        this.orderRepository = orderRepository;
        this.budgetRepository = budgetRepository;
        this.settingsService = settingsService;
    }

    public LoyaltyMetricsDto metrics() {
        LoyaltySettings settings = settingsService.get();
        List<LoyaltyProfile> profiles = profileRepository.findAll();
        long members = profiles.stream().filter(p -> !p.isInHoldout()).count();
        long holdout = profiles.stream().filter(LoyaltyProfile::isInHoldout).count();

        return LoyaltyMetricsDto.builder()
                .members(members)
                .holdout(holdout)
                .budget(budgetMetrics(settings))
                .coinLiability(coinLiability(profiles, settings))
                .tierMix(tierMix(profiles))
                .redemptionByTrigger(redemptionByTrigger())
                .holdoutComparison(holdoutComparison(profiles))
                .build();
    }

    private BudgetMetrics budgetMetrics(LoyaltySettings settings) {
        String month = YearMonth.now().toString();
        DiscountBudget b = budgetRepository.findByMonth(month).orElse(null);
        BigDecimal budgetAmount = b == null ? BigDecimal.ZERO : b.getBudgetAmount();
        BigDecimal spent = b == null ? BigDecimal.ZERO : b.getSpentToDate();
        BigDecimal gross = orderRepository.grossSalesSince(
                YearMonth.now().atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC));
        if (gross == null) gross = BigDecimal.ZERO;
        BigDecimal discountPctOfGross = gross.signum() > 0
                ? spent.divide(gross, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        int pctUsed = budgetAmount.signum() > 0
                ? spent.divide(budgetAmount, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue()
                : 0;
        return BudgetMetrics.builder()
                .month(month).budgetAmount(budgetAmount).spentToDate(spent).grossSales(gross)
                .discountPctOfGross(discountPctOfGross).pctOfBudgetUsed(pctUsed).targetPct(settings.getDiscountPctTarget())
                .build();
    }

    private BigDecimal coinLiability(List<LoyaltyProfile> profiles, LoyaltySettings settings) {
        BigDecimal coins = profiles.stream().map(LoyaltyProfile::getCoinBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return coins.multiply(settings.getCoinValue()).setScale(2, RoundingMode.HALF_UP);
    }

    private List<TierCount> tierMix(List<LoyaltyProfile> profiles) {
        Map<Tier, Long> counts = new EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) counts.put(t, 0L);
        for (LoyaltyProfile p : profiles) counts.merge(p.getTier() == null ? Tier.BRONZE : p.getTier(), 1L, Long::sum);
        List<TierCount> out = new ArrayList<>();
        for (Tier t : Tier.values()) out.add(TierCount.builder().tier(t).count(counts.get(t)).build());
        return out;
    }

    private List<TriggerStat> redemptionByTrigger() {
        Map<OfferTrigger, long[]> stats = new EnumMap<>(OfferTrigger.class);   // [issued, redeemed]
        Map<String, OfferTrigger> triggerByCode = new HashMap<>();
        for (Offer o : offerRepository.findAll()) {
            triggerByCode.put(o.getCode(), o.getTrigger());
            stats.computeIfAbsent(o.getTrigger(), k -> new long[2])[0]++;
        }
        for (Redemption r : redemptionRepository.findAll()) {
            OfferTrigger t = triggerByCode.get(r.getCode());
            if (t != null) stats.computeIfAbsent(t, k -> new long[2])[1]++;
        }
        List<TriggerStat> out = new ArrayList<>();
        stats.forEach((t, s) -> out.add(TriggerStat.builder().trigger(t).issued(s[0]).redeemed(s[1]).build()));
        out.sort((a, b) -> Long.compare(b.getIssued(), a.getIssued()));
        return out;
    }

    private HoldoutComparison holdoutComparison(List<LoyaltyProfile> profiles) {
        Map<Integer, long[]> ordersByUser = new HashMap<>();       // userId → [count, revenueScaled(paise)]
        Map<Integer, BigDecimal> revenueByUser = new HashMap<>();
        for (Object[] row : orderRepository.paidOrderStatsByUser()) {
            Integer userId = (Integer) row[0];
            long count = ((Number) row[1]).longValue();
            BigDecimal revenue = row[2] == null ? BigDecimal.ZERO : new BigDecimal(row[2].toString());
            ordersByUser.put(userId, new long[]{count});
            revenueByUser.put(userId, revenue);
        }
        BigDecimal[] member = groupStats(profiles.stream().filter(p -> !p.isInHoldout()).toList(), ordersByUser, revenueByUser);
        BigDecimal[] hold = groupStats(profiles.stream().filter(LoyaltyProfile::isInHoldout).toList(), ordersByUser, revenueByUser);
        return HoldoutComparison.builder()
                .memberRepeatRate(member[0]).memberAvgRevenue(member[1])
                .holdoutRepeatRate(hold[0]).holdoutAvgRevenue(hold[1])
                .incrementalRevenuePerCustomer(member[1].subtract(hold[1]))
                .build();
    }

    /** Returns [repeatRate (0..1), avgRevenue] for a group of profiles. */
    private BigDecimal[] groupStats(List<LoyaltyProfile> group, Map<Integer, long[]> ordersByUser,
                                    Map<Integer, BigDecimal> revenueByUser) {
        if (group.isEmpty()) return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        long repeat = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        for (LoyaltyProfile p : group) {
            long[] o = ordersByUser.get(p.getUserId());
            if (o != null && o[0] >= 2) repeat++;
            revenue = revenue.add(revenueByUser.getOrDefault(p.getUserId(), BigDecimal.ZERO));
        }
        BigDecimal size = BigDecimal.valueOf(group.size());
        return new BigDecimal[]{
                BigDecimal.valueOf(repeat).divide(size, 3, RoundingMode.HALF_UP),
                revenue.divide(size, 2, RoundingMode.HALF_UP)
        };
    }
}
