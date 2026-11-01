package com.webappfoundation.service;

import com.webappfoundation.dto.AnalyticsSummaryDto;
import com.webappfoundation.dto.AnalyticsSummaryDto.*;
import com.webappfoundation.dto.BulkUploadResult;
import com.webappfoundation.dto.DailyBusinessLogDto;
import com.webappfoundation.dto.UpsertDailyBusinessLogRequest;
import com.webappfoundation.exception.ResourceNotFoundException;
import com.webappfoundation.model.Branch;
import com.webappfoundation.model.DailyBusinessLog;
import com.webappfoundation.model.Order;
import com.webappfoundation.model.PaymentState;
import com.webappfoundation.repository.AnalyticsOrderRepository;
import com.webappfoundation.repository.BranchRepository;
import com.webappfoundation.repository.DailyBusinessLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes the admin Analytics screen. Sales / bills / customers / avg-bill are derived from paid
 * {@link Order} rows (via {@link AnalyticsOrderRepository}); profit, margins and complaints fold in the
 * owner's {@link DailyBusinessLog} entries. Targets are <b>weekday-aware</b>: a day's target is the
 * trailing average of its own recent weekdays (× an optional growth factor), so Monday is judged
 * against Mondays, never Sundays. Comparisons mirror the screen: Today → last same weekday, This week →
 * last week, This month → last month, This year → last year.
 *
 * <p>The order spine is never touched — this reads a bounded window (current financial year + a few
 * weeks of look-back) through its own read-only repository, and scalar sums for older year-history bars.
 */
@Service
public class AnalyticsService {

    /** "A sale" reached payment; refunds are subtracted from net sales via {@code refundAmount}. */
    private static final Set<PaymentState> SALE_STATES =
            EnumSet.of(PaymentState.PAID, PaymentState.REFUND_PENDING, PaymentState.REFUNDED);
    /** How many recent same-weekdays feed a day's target. */
    private static final int WEEKDAY_LOOKBACK = 4;
    private static final BigDecimal LAKH = BigDecimal.valueOf(100_000);
    private static final BigDecimal CRORE = BigDecimal.valueOf(10_000_000);
    private static final String[] WEEKDAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    private final AnalyticsOrderRepository orderRepository;
    private final DailyBusinessLogRepository logRepository;
    private final BranchRepository branchRepository;
    private final SpreadsheetParser spreadsheetParser;
    private final AuditService auditService;

    private final ZoneId zone;
    private final int fyStartMonth;
    private final BigDecimal growthFactor;      // e.g. 0.05 = target 5% above recent pattern
    private final BigDecimal annualGoal;        // ₹ absolute; <=0 = no goal set

    public AnalyticsService(AnalyticsOrderRepository orderRepository,
                            DailyBusinessLogRepository logRepository,
                            BranchRepository branchRepository,
                            SpreadsheetParser spreadsheetParser,
                            AuditService auditService,
                            @Value("${analytics.zone:Asia/Kolkata}") String zoneId,
                            @Value("${analytics.fy-start-month:4}") int fyStartMonth,
                            @Value("${analytics.target-growth-pct:0}") double growthPct,
                            @Value("${analytics.annual-revenue-target:0}") double annualGoal) {
        this.orderRepository = orderRepository;
        this.logRepository = logRepository;
        this.branchRepository = branchRepository;
        this.spreadsheetParser = spreadsheetParser;
        this.auditService = auditService;
        this.zone = ZoneId.of(zoneId);
        this.fyStartMonth = Math.min(12, Math.max(1, fyStartMonth));
        this.growthFactor = BigDecimal.valueOf(growthPct / 100.0);
        this.annualGoal = BigDecimal.valueOf(annualGoal);
    }

    // ══════════════════════════════════ Summary ══════════════════════════════════

    public AnalyticsSummaryDto summary(String periodRaw, Long branchId) {
        String period = periodRaw == null ? "MONTH" : periodRaw.trim().toUpperCase(Locale.ROOT);
        LocalDate today = LocalDate.now(zone);

        // Selected period window + its comparison baseline.
        Range period_ = periodRange(period, today);
        Range baseline = baselineRange(period, today, period_);

        LocalDate fyStart = fyStart(today);
        LocalDate monthStart = today.withDayOfMonth(1);

        // One bounded row load covering every window we touch, plus weekday look-back.
        LocalDate loadFrom = Collections.min(List.of(period_.from, baseline.from, monthStart, fyStart))
                .minusWeeks(WEEKDAY_LOOKBACK + 1L);
        Instant loadFromI = loadFrom.atStartOfDay(zone).toInstant();
        Instant nowI = Instant.now();
        List<Order> allOrders = orderRepository.findByPaymentStateInAndCreatedAtBetween(SALE_STATES, loadFromI, nowI);

        // Branch-scoped working set (the whole screen scopes to the selected outlet).
        List<Order> scoped = branchId == null ? allOrders
                : allOrders.stream().filter(o -> branchId.equals(o.getBranchId())).toList();

        // Per-day net sales for the scoped set (drives targets, day-by-day, weekday averages).
        Map<LocalDate, BigDecimal> dayNet = new HashMap<>();
        for (Order o : scoped) {
            dayNet.merge(dateOf(o), net(o), BigDecimal::add);
        }
        NavigableSet<LocalDate> dataDays = new TreeSet<>(dayNet.keySet());

        // First-ever sale per user → new-vs-repeat classification.
        Map<Integer, LocalDate> firstOrderDate = new HashMap<>();
        for (AnalyticsOrderRepository.UserFirstOrder f : orderRepository.findFirstOrderInstants(SALE_STATES)) {
            firstOrderDate.put(f.getUserId(), f.getFirstAt().atZone(zone).toLocalDate());
        }

        // Daily log entries over the whole load window.
        Map<LocalDate, DailyBusinessLog> logs = logRepository.findByLogDateBetween(loadFrom, today).stream()
                .collect(Collectors.toMap(DailyBusinessLog::getLogDate, l -> l, (a, b) -> a));

        // ── Period + baseline aggregates ──
        Agg cur = aggregate(scoped, firstOrderDate, logs, period_.from, period_.to);
        Agg prev = aggregate(scoped, firstOrderDate, logs, baseline.from, baseline.to);

        // ── 2.2 KPI cards ──
        List<KpiCard> kpis = buildKpis(cur, prev, baseline.label);

        // ── target progress for the selected period ──
        BigDecimal periodTarget = sumTargets(dayNet, dataDays, period_.from, period_.to);
        TargetProgress targetProgress = TargetProgress.builder()
                .actual(cur.netSales).target(periodTarget)
                .achievedPct(ratio(cur.netSales, periodTarget))
                .periodLabel(periodLabel(period, period_, cur.netSales))
                .build();

        // ── 2.3 Year projection (always FY-oriented, like the screen) ──
        Projection projection = buildProjection(dayNet, dataDays, fyStart, today, branchId);

        // ── 2.4 Day-by-day for the current calendar month ──
        DayByDay dayByDay = buildDayByDay(dayNet, dataDays, monthStart, today);

        // ── 2.6 weekday averages (this FY) + year history ──
        List<WeekdayStat> weekdays = buildWeekdays(dayNet, fyStart, today);
        List<YearlyStat> yearly = buildYearly(today, projection.getExpectedSales());

        // per-branch split for the period (always all outlets)
        List<BranchStat> branches = buildBranchSplit(allOrders, period_.from, period_.to);

        // ── 2.5 attention list + 2.1 banner counts ──
        List<Track> tracks = buildTracks(cur, prev, targetProgress, dayByDay, baseline.label);
        List<AttentionItem> attention = tracks.stream()
                .filter(t -> !"OK".equals(t.severity))
                .map(t -> AttentionItem.builder()
                        .severity(t.severity).title(t.title).detail(t.detail).action(t.action).build())
                .toList();
        Banner banner = buildBanner(projection, tracks);

        return AnalyticsSummaryDto.builder()
                .period(period).branchId(branchId).asOf(today).currency("INR")
                .outletCount((int) branchRepository.findByActiveTrue().stream().count())
                .banner(banner).kpis(kpis).targetProgress(targetProgress).projection(projection)
                .dayByDay(dayByDay).attention(attention).weekdays(weekdays).yearly(yearly).branches(branches)
                .build();
    }

    // ── aggregates ──

    private record Range(LocalDate from, LocalDate to, String label) {}

    private static final class Agg {
        BigDecimal netSales = BigDecimal.ZERO;
        int bills = 0;
        Set<Integer> buyers = new HashSet<>();
        int newCustomers = 0;
        BigDecimal cogs = BigDecimal.ZERO;
        BigDecimal opex = BigDecimal.ZERO;
        int complaints = 0;
        boolean hasCost = false;

        BigDecimal grossProfit() { return netSales.subtract(cogs); }
        BigDecimal netProfit()   { return netSales.subtract(cogs).subtract(opex); }
        int customers()          { return buyers.size(); }
        BigDecimal avgBill()     { return bills == 0 ? BigDecimal.ZERO : netSales.divide(BigDecimal.valueOf(bills), 2, RoundingMode.HALF_UP); }
    }

    private Agg aggregate(List<Order> orders, Map<Integer, LocalDate> firstOrderDate,
                          Map<LocalDate, DailyBusinessLog> logs, LocalDate from, LocalDate to) {
        Agg a = new Agg();
        for (Order o : orders) {
            LocalDate d = dateOf(o);
            if (d.isBefore(from) || d.isAfter(to)) continue;
            a.netSales = a.netSales.add(net(o));
            a.bills++;
            Integer uid = o.getUserId();
            if (uid != null) {
                a.buyers.add(uid);
                LocalDate first = firstOrderDate.get(uid);
                if (first != null && !first.isBefore(from) && !first.isAfter(to)) a.newCustomers++;
            }
        }
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DailyBusinessLog l = logs.get(d);
            if (l == null) continue;
            if (l.getCogs() != null) a.cogs = a.cogs.add(l.getCogs());
            if (l.getOperatingExpenses() != null) a.opex = a.opex.add(l.getOperatingExpenses());
            a.complaints += l.getComplaints();
            if (sig(l.getCogs()) || sig(l.getOperatingExpenses())) a.hasCost = true;
        }
        return a;
    }

    // ── KPI cards ──

    private List<KpiCard> buildKpis(Agg cur, Agg prev, String cmpLabel) {
        List<KpiCard> cards = new ArrayList<>();
        cards.add(KpiCard.builder().key("sales").label("Sales")
                .value(cur.netSales).display(money(cur.netSales))
                .changePct(change(cur.netSales, prev.netSales)).direction(dir(cur.netSales, prev.netSales))
                .comparisonLabel(cmpLabel).build());

        boolean cost = cur.hasCost;
        BigDecimal margin = cost && sig(cur.netSales)
                ? cur.netProfit().divide(cur.netSales, 4, RoundingMode.HALF_UP) : null;
        cards.add(KpiCard.builder().key("profit").label("Profit")
                .value(cost ? cur.netProfit() : null)
                .display(cost ? money(cur.netProfit()) : "No cost data yet")
                .changePct(cost && prev.hasCost ? change(cur.netProfit(), prev.netProfit()) : null)
                .direction(cost && prev.hasCost ? dir(cur.netProfit(), prev.netProfit()) : "NONE")
                .comparisonLabel(cmpLabel)
                .note(margin == null ? null : paisaNote(margin)).build());

        cards.add(KpiCard.builder().key("customers").label("Customers who bought")
                .value(BigDecimal.valueOf(cur.customers())).display(intIndian(cur.customers()))
                .changePct(change(cur.customers(), prev.customers())).direction(dir(cur.customers(), prev.customers()))
                .comparisonLabel(cmpLabel)
                .note(cur.newCustomers + " were new").build());

        cards.add(KpiCard.builder().key("avgBill").label("Average bill")
                .value(cur.avgBill()).display(money(cur.avgBill()))
                .changePct(change(cur.avgBill(), prev.avgBill())).direction(dir(cur.avgBill(), prev.avgBill()))
                .comparisonLabel(cmpLabel).note("Per customer").build());
        return cards;
    }

    // ── targets ──

    /** Target for a single day = trailing average of its recent same-weekday net sales × (1 + growth). null = no history. */
    private BigDecimal targetFor(Map<LocalDate, BigDecimal> dayNet, NavigableSet<LocalDate> dataDays, LocalDate d) {
        if (dataDays.isEmpty()) return null;
        LocalDate earliest = dataDays.first();
        List<BigDecimal> samples = new ArrayList<>();
        for (LocalDate c = d.minusWeeks(1); !c.isBefore(earliest) && samples.size() < WEEKDAY_LOOKBACK; c = c.minusWeeks(1)) {
            BigDecimal v = dayNet.get(c);
            if (sig(v)) samples.add(v);
        }
        if (samples.isEmpty()) return null;
        BigDecimal avg = samples.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(samples.size()), 2, RoundingMode.HALF_UP);
        return avg.add(avg.multiply(growthFactor)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumTargets(Map<LocalDate, BigDecimal> dayNet, NavigableSet<LocalDate> dataDays,
                                  LocalDate from, LocalDate to) {
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            BigDecimal t = targetFor(dayNet, dataDays, d);
            if (t != null) { sum = sum.add(t); n++; }
        }
        return n == 0 ? null : sum;
    }

    // ── projection ──

    private Projection buildProjection(Map<LocalDate, BigDecimal> dayNet, NavigableSet<LocalDate> dataDays,
                                       LocalDate fyStart, LocalDate today, Long branchId) {
        LocalDate fyEnd = fyStart.plusYears(1).minusDays(1);
        BigDecimal actualSoFar = sumDayNet(dayNet, fyStart, today);
        BigDecimal remaining = BigDecimal.ZERO;
        for (LocalDate d = today.plusDays(1); !d.isAfter(fyEnd); d = d.plusDays(1)) {
            BigDecimal t = targetFor(dayNet, dataDays, d);
            if (t != null) remaining = remaining.add(t);
        }
        BigDecimal expected = actualSoFar.add(remaining);

        BigDecimal goal = sig(annualGoal) ? annualGoal : null;
        // Last year is only shown for the all-outlets view (scalar sum has no branch filter).
        BigDecimal lastYear = null;
        if (branchId == null) {
            BigDecimal ly = orderRepository.sumNetSales(SALE_STATES,
                    fyStart.minusYears(1).atStartOfDay(zone).toInstant(), fyStart.atStartOfDay(zone).toInstant());
            if (sig(ly)) lastYear = ly;
        }

        // expected profit: extrapolate the FY-to-date net margin if we have cost data.
        Map<LocalDate, DailyBusinessLog> fyLogs = logRepository.findByLogDateBetween(fyStart, today).stream()
                .collect(Collectors.toMap(DailyBusinessLog::getLogDate, l -> l, (a, b) -> a));
        BigDecimal costSoFar = BigDecimal.ZERO, opexSoFar = BigDecimal.ZERO;
        boolean hasCost = false;
        for (DailyBusinessLog l : fyLogs.values()) {
            if (l.getCogs() != null) costSoFar = costSoFar.add(l.getCogs());
            if (l.getOperatingExpenses() != null) opexSoFar = opexSoFar.add(l.getOperatingExpenses());
            if (sig(l.getCogs()) || sig(l.getOperatingExpenses())) hasCost = true;
        }
        BigDecimal expectedProfit = null;
        if (hasCost && sig(actualSoFar)) {
            BigDecimal marginSoFar = actualSoFar.subtract(costSoFar).subtract(opexSoFar)
                    .divide(actualSoFar, 4, RoundingMode.HALF_UP);
            expectedProfit = expected.multiply(marginSoFar).setScale(2, RoundingMode.HALF_UP);
        }

        int monthsGone = (int) java.time.temporal.ChronoUnit.MONTHS.between(
                YearMonth.from(fyStart), YearMonth.from(today)) + 1;

        return Projection.builder()
                .actualSoFar(actualSoFar).expectedSales(expected).goal(goal).lastYearSales(lastYear)
                .salesChangePct(lastYear == null ? null : change(expected, lastYear))
                .expectedProfit(expectedProfit)
                .profitChangePct(null)
                .yearGoalDonePct(goal == null ? 0 : ratio(actualSoFar, goal))
                .monthsGone(Math.min(12, Math.max(1, monthsGone)))
                .series(buildSeries(dayNet, dataDays, fyStart, fyEnd, today, goal, branchId))
                .build();
    }

    private List<CumulativePoint> buildSeries(Map<LocalDate, BigDecimal> dayNet, NavigableSet<LocalDate> dataDays,
                                              LocalDate fyStart, LocalDate fyEnd, LocalDate today,
                                              BigDecimal goal, Long branchId) {
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(fyStart, fyEnd) + 1;
        List<CumulativePoint> pts = new ArrayList<>();
        BigDecimal cumActual = BigDecimal.ZERO, cumProjected = BigDecimal.ZERO;
        // optional last-year monthly cumulative (all outlets only)
        BigDecimal cumLast = BigDecimal.ZERO;
        boolean lastYearAvailable = branchId == null;

        for (int i = 0; i < 12; i++) {
            YearMonth ym = YearMonth.from(fyStart.plusMonths(i));
            LocalDate mEnd = ym.atEndOfMonth();
            LocalDate capEnd = mEnd.isAfter(today) ? today : mEnd;

            BigDecimal actual = null;
            if (!ym.atDay(1).isAfter(today)) {
                cumActual = cumActual.add(sumDayNet(dayNet, ym.atDay(1), capEnd));
                actual = cumActual;
            }
            // projected tracks actual through today, then adds future targets
            cumProjected = cumActual.add(futureTargetSum(dayNet, dataDays, today, mEnd));

            BigDecimal goalCum = null;
            if (goal != null) {
                long elapsed = java.time.temporal.ChronoUnit.DAYS.between(fyStart, mEnd) + 1;
                goalCum = goal.multiply(BigDecimal.valueOf(elapsed))
                        .divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
            }
            BigDecimal lastCum = null;
            if (lastYearAvailable) {
                YemMonthWindow w = prevYearMonthWindow(ym);
                BigDecimal ly = orderRepository.sumNetSales(SALE_STATES, w.from, w.to);
                cumLast = cumLast.add(ly == null ? BigDecimal.ZERO : ly);
                lastCum = cumLast;
            }
            pts.add(CumulativePoint.builder()
                    .label(ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                    .actual(actual)
                    .projected(cumProjected)
                    .goal(goalCum)
                    .lastYear(lastCum)
                    .build());
        }
        return pts;
    }

    private record YemMonthWindow(Instant from, Instant to) {}

    private YemMonthWindow prevYearMonthWindow(YearMonth ym) {
        YearMonth prev = ym.minusYears(1);
        return new YemMonthWindow(
                prev.atDay(1).atStartOfDay(zone).toInstant(),
                prev.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant());
    }

    private BigDecimal futureTargetSum(Map<LocalDate, BigDecimal> dayNet, NavigableSet<LocalDate> dataDays,
                                       LocalDate today, LocalDate to) {
        BigDecimal sum = BigDecimal.ZERO;
        for (LocalDate d = today.plusDays(1); !d.isAfter(to); d = d.plusDays(1)) {
            BigDecimal t = targetFor(dayNet, dataDays, d);
            if (t != null) sum = sum.add(t);
        }
        return sum;
    }

    // ── day by day ──

    private DayByDay buildDayByDay(Map<LocalDate, BigDecimal> dayNet, NavigableSet<LocalDate> dataDays,
                                   LocalDate monthStart, LocalDate today) {
        YearMonth ym = YearMonth.from(monthStart);
        LocalDate monthEnd = ym.atEndOfMonth();
        List<DayBar> bars = new ArrayList<>();
        BigDecimal soldSoFar = BigDecimal.ZERO, monthTarget = BigDecimal.ZERO, remainingTarget = BigDecimal.ZERO;
        int daysMet = 0, daysElapsed = 0;
        for (LocalDate d = monthStart; !d.isAfter(monthEnd); d = d.plusDays(1)) {
            boolean future = d.isAfter(today);
            BigDecimal sales = future ? BigDecimal.ZERO : dayNet.getOrDefault(d, BigDecimal.ZERO);
            BigDecimal target = targetFor(dayNet, dataDays, d);
            boolean met = !future && target != null && sales.compareTo(target) >= 0;
            if (target != null) monthTarget = monthTarget.add(target);
            if (!future) {
                soldSoFar = soldSoFar.add(sales);
                daysElapsed++;
                if (met) daysMet++;
            } else if (target != null) {
                remainingTarget = remainingTarget.add(target);
            }
            bars.add(DayBar.builder().date(d).dayOfMonth(d.getDayOfMonth())
                    .sales(sales).target(target).met(met).future(future).build());
        }
        BigDecimal expectedEnd = soldSoFar.add(remainingTarget);
        return DayByDay.builder()
                .soldSoFar(soldSoFar).monthTarget(sig(monthTarget) ? monthTarget : null)
                .expectedMonthEnd(expectedEnd).monthAchievedPct(ratio(expectedEnd, monthTarget))
                .daysMet(daysMet).daysElapsed(daysElapsed).bars(bars).build();
    }

    // ── weekday averages ──

    private List<WeekdayStat> buildWeekdays(Map<LocalDate, BigDecimal> dayNet, LocalDate fyStart, LocalDate today) {
        BigDecimal[] sum = new BigDecimal[7];
        int[] cnt = new int[7];
        Arrays.fill(sum, BigDecimal.ZERO);
        for (LocalDate d = fyStart; !d.isAfter(today); d = d.plusDays(1)) {
            BigDecimal v = dayNet.get(d);
            if (!sig(v)) continue;
            int idx = d.getDayOfWeek().getValue() - 1; // Mon=0
            sum[idx] = sum[idx].add(v);
            cnt[idx]++;
        }
        List<WeekdayStat> out = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            BigDecimal avg = cnt[i] == 0 ? BigDecimal.ZERO
                    : sum[i].divide(BigDecimal.valueOf(cnt[i]), 2, RoundingMode.HALF_UP);
            out.add(WeekdayStat.builder().day(WEEKDAYS[i]).avgSales(avg).weekend(i >= 5).build());
        }
        return out;
    }

    // ── year history ──

    private List<YearlyStat> buildYearly(LocalDate today, BigDecimal expectedThisFy) {
        LocalDate curFyStart = fyStart(today);
        int curStartYear = curFyStart.getYear();
        Optional<Order> earliest = orderRepository.findFirstByPaymentStateInOrderByCreatedAtAsc(SALE_STATES);
        int firstStartYear = earliest.map(o -> fyStart(dateOf(o)).getYear()).orElse(curStartYear);
        int from = Math.max(firstStartYear, curStartYear - 5);
        List<YearlyStat> out = new ArrayList<>();
        for (int y = from; y <= curStartYear; y++) {
            LocalDate s = LocalDate.of(y, fyStartMonth, 1);
            boolean current = y == curStartYear;
            BigDecimal sales;
            if (current) {
                sales = expectedThisFy;
            } else {
                sales = orderRepository.sumNetSales(SALE_STATES,
                        s.atStartOfDay(zone).toInstant(), s.plusYears(1).atStartOfDay(zone).toInstant());
            }
            out.add(YearlyStat.builder().label(fyLabel(y)).sales(sales == null ? BigDecimal.ZERO : sales)
                    .projected(current).build());
        }
        return out;
    }

    // ── per-branch split ──

    private List<BranchStat> buildBranchSplit(List<Order> allOrders, LocalDate from, LocalDate to) {
        Map<Long, BranchStat> byBranch = new LinkedHashMap<>();
        Map<Long, BigDecimal> sales = new HashMap<>();
        Map<Long, Integer> bills = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (Order o : allOrders) {
            LocalDate d = dateOf(o);
            if (d.isBefore(from) || d.isAfter(to)) continue;
            Long bId = o.getBranchId();
            sales.merge(bId, net(o), BigDecimal::add);
            bills.merge(bId, 1, Integer::sum);
            names.putIfAbsent(bId, o.getBranchName());
        }
        List<BranchStat> out = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : sales.entrySet()) {
            Long bId = e.getKey();
            String name = names.get(bId);
            if (name == null) name = bId == null ? "Unassigned" : "Branch " + bId;
            out.add(BranchStat.builder().branchId(bId).name(name)
                    .sales(e.getValue()).bills(bills.getOrDefault(bId, 0)).build());
        }
        out.sort(Comparator.comparing(BranchStat::getSales, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    // ── attention + banner ──

    /** Internal tracked metric with a traffic-light severity (OK | WATCH | ACT_NOW). */
    private static final class Track {
        String severity, title, detail, action;
        Track(String severity, String title, String detail, String action) {
            this.severity = severity; this.title = title; this.detail = detail; this.action = action;
        }
    }

    private List<Track> buildTracks(Agg cur, Agg prev, TargetProgress tp, DayByDay dbd, String cmpLabel) {
        List<Track> t = new ArrayList<>();

        // Sales vs target (period)
        if (tp.getTarget() != null) {
            t.add(new Track(vsTarget(tp.getAchievedPct()),
                    "Sales vs target",
                    money(tp.getActual()) + " of " + money(tp.getTarget()) + " target ("
                            + pct(tp.getAchievedPct()) + ")",
                    "WhatsApp/SMS past customers with a reason to visit; push combos; check which outlet is dragging."));
        }
        // Daily targets hit-rate (this month)
        if (dbd.getDaysElapsed() > 0 && dbd.getMonthTarget() != null) {
            double hit = dbd.getDaysElapsed() == 0 ? 1.0 : (double) dbd.getDaysMet() / dbd.getDaysElapsed();
            t.add(new Track(hit < 0.6 ? "ACT_NOW" : hit < 0.85 ? "WATCH" : "OK",
                    "Daily targets",
                    "Met on " + dbd.getDaysMet() + " of " + dbd.getDaysElapsed() + " days this month",
                    "Work out the daily sales needed and share it with every store manager each morning."));
        }
        // Sales growth vs comparable period (higher better)
        t.add(new Track(higherBetter(cur.netSales, prev.netSales),
                "Sales growth",
                money(cur.netSales) + " (" + signed(change(cur.netSales, prev.netSales)) + " " + cmpLabel + ")",
                "Grow customers, conversion and bill size — the three levers behind every sale."));

        // Average bill (higher better)
        t.add(new Track(higherBetter(cur.avgBill(), prev.avgBill()),
                "Average bill",
                money(cur.avgBill()) + " (" + signed(change(cur.avgBill(), prev.avgBill())) + " " + cmpLabel + ")",
                "Upsell, combos, spend thresholds and impulse items at billing."));

        // Repeat %
        int repeatCur = cur.customers() - cur.newCustomers;
        int repeatPrev = prev.customers() - prev.newCustomers;
        double repeatRateCur = cur.customers() == 0 ? 0 : (double) repeatCur / cur.customers();
        if (cur.customers() > 0) {
            t.add(new Track(higherBetter(repeatCur, repeatPrev),
                    "Repeat customers",
                    pct(repeatRateCur) + " of buyers are returning",
                    "Loyalty points, WhatsApp follow-ups, birthday offers; fix complaints fast."));
        }

        // Margins + complaints — only when cost / complaint data exists
        if (cur.hasCost && sig(cur.netSales)) {
            double gmCur = cur.grossProfit().divide(cur.netSales, 4, RoundingMode.HALF_UP).doubleValue();
            double gmPrev = prev.hasCost && sig(prev.netSales)
                    ? prev.grossProfit().divide(prev.netSales, 4, RoundingMode.HALF_UP).doubleValue() : gmCur;
            t.add(new Track(higherBetterD(gmCur, gmPrev),
                    "Gross margin",
                    pct(gmCur) + " this period",
                    "Supplier negotiation, less wastage, better product mix, fewer markdowns."));
            double nmCur = cur.netProfit().divide(cur.netSales, 4, RoundingMode.HALF_UP).doubleValue();
            double nmPrev = prev.hasCost && sig(prev.netSales)
                    ? prev.netProfit().divide(prev.netSales, 4, RoundingMode.HALF_UP).doubleValue() : nmCur;
            t.add(new Track(higherBetterD(nmCur, nmPrev),
                    "Net margin",
                    pct(nmCur) + " this period",
                    "Fix the biggest red cost line first."));
        }
        if (cur.complaints > 0 || prev.complaints > 0) {
            double rateCur = cur.bills == 0 ? 0 : (double) cur.complaints / cur.bills * 1000;
            double ratePrev = prev.bills == 0 ? 0 : (double) prev.complaints / prev.bills * 1000;
            t.add(new Track(lowerBetterD(rateCur, ratePrev),
                    "Complaints",
                    round1(rateCur) + " per 1,000 bills (was " + round1(ratePrev) + ")",
                    "Read every complaint daily, find the top cause and fix it within a week."));
        }
        return t;
    }

    private Banner buildBanner(Projection p, List<Track> tracks) {
        int ok = 0, watch = 0, act = 0;
        for (Track t : tracks) {
            switch (t.severity) { case "OK" -> ok++; case "WATCH" -> watch++; default -> act++; }
        }
        boolean growing = p.getSalesChangePct() == null ? sig(p.getExpectedSales()) : p.getSalesChangePct() > 0;
        StringBuilder head = new StringBuilder();
        head.append(growing ? "The business is growing. " : "Sales need attention. ");
        head.append("This year is on course for ").append(money(p.getExpectedSales()));
        if (p.getSalesChangePct() != null) {
            head.append(", ").append(signed(p.getSalesChangePct())).append(" vs last year");
        }
        head.append(".");
        String sub;
        if (p.getGoal() != null) {
            boolean met = p.getExpectedSales().compareTo(p.getGoal()) >= 0;
            sub = "The " + money(p.getGoal()) + " goal is " + (met ? "within reach" : "just out of reach")
                    + " at the current pace.";
        } else {
            sub = "Set an annual goal to track progress against target.";
        }
        return Banner.builder().growing(growing).headline(head.toString()).subline(sub)
                .onTrack(ok).toWatch(watch).needAction(act).totalTracked(ok + watch + act).build();
    }

    // ══════════════════════════════════ Daily log CRUD ══════════════════════════════════

    public List<DailyBusinessLogDto> listLogs() {
        return logRepository.findAllByOrderByLogDateDesc().stream().map(this::toDto).toList();
    }

    public DailyBusinessLogDto getLog(LocalDate date) {
        return toDto(logRepository.findByLogDate(date)
                .orElseThrow(() -> new ResourceNotFoundException("No daily log for " + date)));
    }

    @Transactional
    public DailyBusinessLogDto upsert(UpsertDailyBusinessLogRequest req) {
        if (req.getLogDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "logDate is required");
        }
        Instant now = Instant.now();
        DailyBusinessLog log = logRepository.findByLogDate(req.getLogDate())
                .orElseGet(() -> DailyBusinessLog.builder().logDate(req.getLogDate()).createdAt(now).build());
        log.setCogs(nz(req.getCogs()));
        log.setOperatingExpenses(nz(req.getOperatingExpenses()));
        log.setComplaints(req.getComplaints() == null ? 0 : Math.max(0, req.getComplaints()));
        log.setNote(req.getNote());
        log.setUpdatedAt(now);
        DailyBusinessLog saved = logRepository.save(log);
        auditService.record("ANALYTICS_DAILY_LOG_SAVED", AuditActions.SUCCESS, "DAILY_BUSINESS_LOG",
                String.valueOf(saved.getId()), "date=" + saved.getLogDate());
        return toDto(saved);
    }

    @Transactional
    public void deleteLog(Long id) {
        DailyBusinessLog log = logRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Daily log not found: " + id));
        logRepository.delete(log);
        auditService.record("ANALYTICS_DAILY_LOG_DELETED", AuditActions.SUCCESS, "DAILY_BUSINESS_LOG",
                String.valueOf(id), "date=" + log.getLogDate());
    }

    /** Bulk import. Columns: {@code date, cogs, operatingexpenses, complaints, note} (date required, ISO yyyy-MM-dd). */
    @Transactional
    public BulkUploadResult bulkImport(MultipartFile file) {
        BulkUploadResult result = BulkUploadResult.builder().build();
        List<Map<String, String>> rows = spreadsheetParser.parse(file);
        int rowNum = 1;
        for (Map<String, String> row : rows) {
            rowNum++;
            String rawDate = row.getOrDefault("date", "").trim();
            if (rawDate.isEmpty()) { result.addError(rowNum, "date is required"); continue; }
            LocalDate date;
            try { date = LocalDate.parse(rawDate); }
            catch (Exception e) { result.addError(rowNum, "invalid date (use yyyy-MM-dd): " + rawDate); continue; }
            Instant now = Instant.now();
            DailyBusinessLog log = logRepository.findByLogDate(date)
                    .orElseGet(() -> DailyBusinessLog.builder().logDate(date).createdAt(now).build());
            boolean existed = log.getId() != null;
            log.setCogs(parseMoney(row.get("cogs")));
            log.setOperatingExpenses(parseMoney(row.get("operatingexpenses")));
            log.setComplaints(parseInt(row.get("complaints")));
            log.setNote(emptyToNull(row.getOrDefault("note", "")));
            log.setUpdatedAt(now);
            logRepository.save(log);
            if (existed) result.setSkipped(result.getSkipped() + 1); else result.setCreated(result.getCreated() + 1);
        }
        auditService.record("ANALYTICS_DAILY_LOG_BULK_IMPORT", AuditActions.SUCCESS, "DAILY_BUSINESS_LOG", null,
                "created=" + result.getCreated() + " updated=" + result.getSkipped() + " failed=" + result.getFailed());
        return result;
    }

    // ══════════════════════════════════ helpers ══════════════════════════════════

    private Range periodRange(String period, LocalDate today) {
        return switch (period) {
            case "TODAY" -> new Range(today, today, null);
            case "WEEK" -> new Range(today.with(DayOfWeek.MONDAY), today, null);
            case "YEAR" -> new Range(fyStart(today), today, null);
            default -> new Range(today.withDayOfMonth(1), today, null); // MONTH
        };
    }

    private Range baselineRange(String period, LocalDate today, Range cur) {
        return switch (period) {
            case "TODAY" -> new Range(today.minusWeeks(1), today.minusWeeks(1),
                    "vs last " + today.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            case "WEEK" -> new Range(cur.from.minusWeeks(1), cur.to.minusWeeks(1), "vs last week");
            case "YEAR" -> {
                long elapsed = java.time.temporal.ChronoUnit.DAYS.between(cur.from, cur.to);
                LocalDate pf = cur.from.minusYears(1);
                yield new Range(pf, pf.plusDays(elapsed), "vs last year");
            }
            default -> {
                long elapsed = java.time.temporal.ChronoUnit.DAYS.between(cur.from, cur.to);
                LocalDate pm = cur.from.minusMonths(1);
                yield new Range(pm, pm.plusDays(elapsed), "vs last month");
            }
        };
    }

    private String periodLabel(String period, Range r, BigDecimal actual) {
        String span = switch (period) {
            case "TODAY" -> r.from.getDayOfMonth() + " " + r.from.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + r.from.getYear();
            case "WEEK" -> "Week of " + r.from.getDayOfMonth() + " " + r.from.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            case "YEAR" -> r.from.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + "–"
                    + r.to.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + r.to.getYear();
            default -> r.to.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + r.to.getYear();
        };
        return span + " (" + money(actual) + ")";
    }

    private LocalDate fyStart(LocalDate d) {
        int startYear = d.getMonthValue() >= fyStartMonth ? d.getYear() : d.getYear() - 1;
        return LocalDate.of(startYear, fyStartMonth, 1);
    }

    private String fyLabel(int startYear) {
        return "FY" + String.format("%02d", (startYear + 1) % 100);
    }

    private BigDecimal sumDayNet(Map<LocalDate, BigDecimal> dayNet, LocalDate from, LocalDate to) {
        BigDecimal sum = BigDecimal.ZERO;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            sum = sum.add(dayNet.getOrDefault(d, BigDecimal.ZERO));
        }
        return sum;
    }

    private LocalDate dateOf(Order o) { return o.getCreatedAt().atZone(zone).toLocalDate(); }

    private static BigDecimal net(Order o) {
        BigDecimal total = o.getTotal() == null ? BigDecimal.ZERO : o.getTotal();
        BigDecimal refund = o.getRefundAmount() == null ? BigDecimal.ZERO : o.getRefundAmount();
        return total.subtract(refund);
    }

    private DailyBusinessLogDto toDto(DailyBusinessLog l) {
        return DailyBusinessLogDto.builder()
                .id(l.getId()).logDate(l.getLogDate()).cogs(l.getCogs())
                .operatingExpenses(l.getOperatingExpenses()).complaints(l.getComplaints())
                .note(l.getNote()).createdAt(l.getCreatedAt()).updatedAt(l.getUpdatedAt()).build();
    }

    // numeric helpers — percentages returned as fractions (0.19 = +19%); the UI multiplies by 100.
    private static boolean sig(BigDecimal v) { return v != null && v.signum() != 0; }
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static Double change(BigDecimal cur, BigDecimal prev) {
        if (prev == null || prev.signum() == 0) return null;
        return cur.subtract(prev).divide(prev, 4, RoundingMode.HALF_UP).doubleValue();
    }
    private static Double change(long cur, long prev) {
        if (prev == 0) return null;
        return (double) (cur - prev) / prev;
    }
    private static double ratio(BigDecimal num, BigDecimal den) {
        if (den == null || den.signum() == 0) return 0;
        return num.divide(den, 4, RoundingMode.HALF_UP).doubleValue();
    }
    private static String dir(BigDecimal cur, BigDecimal prev) {
        if (prev == null || prev.signum() == 0) return "NONE";
        int c = cur.compareTo(prev);
        return c > 0 ? "UP" : c < 0 ? "DOWN" : "FLAT";
    }
    private static String dir(long cur, long prev) {
        if (prev == 0) return "NONE";
        return cur > prev ? "UP" : cur < prev ? "DOWN" : "FLAT";
    }
    private static String vsTarget(double achieved) {
        return achieved < 0.9 ? "ACT_NOW" : achieved < 1.0 ? "WATCH" : "OK";
    }
    private static String higherBetter(BigDecimal cur, BigDecimal prev) {
        Double d = change(cur, prev);
        return d == null ? "OK" : d < -0.1 ? "ACT_NOW" : d < 0 ? "WATCH" : "OK";
    }
    private static String higherBetter(long cur, long prev) {
        Double d = change(cur, prev);
        return d == null ? "OK" : d < -0.1 ? "ACT_NOW" : d < 0 ? "WATCH" : "OK";
    }
    private static String higherBetterD(double cur, double prev) {
        if (prev == 0) return "OK";
        double d = (cur - prev) / Math.abs(prev);
        return d < -0.1 ? "ACT_NOW" : d < 0 ? "WATCH" : "OK";
    }
    private static String lowerBetterD(double cur, double prev) {
        if (prev == 0) return cur > 0 ? "WATCH" : "OK";
        double d = (cur - prev) / Math.abs(prev);
        return d > 0.1 ? "ACT_NOW" : d > 0 ? "WATCH" : "OK";
    }

    // ── formatting (Indian ₹ L/Cr + grouping) ──
    private static String money(BigDecimal v) {
        if (v == null) return "—";
        BigDecimal abs = v.abs();
        String sign = v.signum() < 0 ? "-" : "";
        if (abs.compareTo(CRORE) >= 0) {
            return sign + "₹" + trim(abs.divide(CRORE, 1, RoundingMode.HALF_UP)) + " Cr";
        }
        if (abs.compareTo(LAKH) >= 0) {
            return sign + "₹" + trim(abs.divide(LAKH, 1, RoundingMode.HALF_UP)) + " L";
        }
        return sign + "₹" + groupIndian(abs.setScale(0, RoundingMode.HALF_UP).toBigInteger().toString());
    }
    private static String trim(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
    private static String intIndian(long v) { return groupIndian(Long.toString(Math.abs(v))); }
    /** Indian digit grouping: 1,43,052. */
    private static String groupIndian(String digits) {
        if (digits.length() <= 3) return digits;
        String head = digits.substring(0, digits.length() - 3);
        String tail = digits.substring(digits.length() - 3);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = head.length() - 1; i >= 0; i--) {
            sb.append(head.charAt(i));
            if (++count % 2 == 0 && i != 0) sb.append(',');
        }
        return sb.reverse() + "," + tail;
    }
    private static String pct(double fraction) {
        return Math.round(fraction * 100) + "%";
    }
    private static String signed(Double fraction) {
        if (fraction == null) return "no prior data";
        long p = Math.round(fraction * 100);
        return (p >= 0 ? "+" : "") + p + "%";
    }
    private static String round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    private static String paisaNote(BigDecimal marginFraction) {
        long paisa = marginFraction.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
        return paisa + "p kept from every ₹1";
    }

    private static String emptyToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
    private static BigDecimal parseMoney(String v) {
        if (v == null || v.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(v.trim().replace(",", "")); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
    private static int parseInt(String v) {
        if (v == null || v.isBlank()) return 0;
        try { return Math.max(0, Integer.parseInt(v.trim())); } catch (NumberFormatException e) { return 0; }
    }
}
