package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the admin Analytics screen renders, for one selected period + branch scope. All sales/
 * bills/customers figures are derived from paid orders; profit/margin/complaints fold in the
 * {@link com.webappfoundation.model.DailyBusinessLog} entries. Targets are weekday-aware (a day's
 * target is built from its own past weekdays, so Monday is judged against Mondays).
 *
 * <p>Nested static classes map one-to-one to the screen's sections (banner → KPI cards → year
 * projection → day-by-day → attention list → weekday/year-history → per-branch split).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSummaryDto {

    /** TODAY | WEEK | MONTH | YEAR. */
    private String period;
    /** null = all outlets. */
    private Long branchId;
    private LocalDate asOf;
    private String currency;
    private int outletCount;

    private Banner banner;
    private List<KpiCard> kpis;
    private TargetProgress targetProgress;
    private Projection projection;
    private DayByDay dayByDay;
    private List<AttentionItem> attention;
    private List<WeekdayStat> weekdays;
    private List<YearlyStat> yearly;
    private List<BranchStat> branches;

    // ── 2.1 The short answer ──
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Banner {
        private boolean growing;
        private String headline;      // one-sentence summary
        private String subline;       // secondary line (goal / projection)
        private int onTrack;
        private int toWatch;
        private int needAction;
        private int totalTracked;
    }

    // ── 2.2 How we are doing (one card per KPI) ──
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class KpiCard {
        private String key;            // sales | profit | customers | avgBill
        private String label;
        private BigDecimal value;
        private String display;        // pre-formatted (e.g. "₹37.4 Cr")
        private Double changePct;      // null = no comparable prior period yet
        private String direction;      // UP | DOWN | FLAT | NONE
        private String comparisonLabel;// e.g. "vs last Friday"
        private String note;           // e.g. "91,589 were new" / "11p kept from every ₹1"
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TargetProgress {
        private BigDecimal actual;
        private BigDecimal target;
        private double achievedPct;    // actual / target (1.0 = on target)
        private String periodLabel;    // e.g. "Apr–Oct 2026 (₹37.4 Cr)"
    }

    // ── 2.3 Where the year is heading ──
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Projection {
        private BigDecimal actualSoFar;
        private BigDecimal expectedSales;
        private BigDecimal goal;             // null if no annual goal set
        private BigDecimal lastYearSales;    // null if no prior-year data
        private Double salesChangePct;       // expected vs last year
        private BigDecimal expectedProfit;   // null if no cost data
        private Double profitChangePct;
        private double yearGoalDonePct;      // actualSoFar / goal
        private int monthsGone;              // of 12
        private List<CumulativePoint> series;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CumulativePoint {
        private String label;          // "Apr", "May", …
        private BigDecimal actual;     // cumulative actual (null for future months)
        private BigDecimal projected;  // cumulative expected-at-pace
        private BigDecimal goal;       // cumulative goal line (null if no goal)
        private BigDecimal lastYear;   // cumulative last year (null if no data)
    }

    // ── 2.4 Day by day (current month) ──
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DayByDay {
        private BigDecimal soldSoFar;
        private BigDecimal monthTarget;
        private BigDecimal expectedMonthEnd;
        private double monthAchievedPct;     // expectedMonthEnd / monthTarget
        private int daysMet;
        private int daysElapsed;
        private List<DayBar> bars;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DayBar {
        private LocalDate date;
        private int dayOfMonth;
        private BigDecimal sales;
        private BigDecimal target;
        private boolean met;
        private boolean future;        // no data yet (today onward)
    }

    // ── 2.5 What needs attention ──
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AttentionItem {
        private String severity;       // ACT_NOW | WATCH
        private String title;
        private String detail;         // the current number, in words
        private String action;         // suggested fix
    }

    // ── 2.6 Which days make the money / six years of growth ──
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class WeekdayStat {
        private String day;            // Mon..Sun
        private BigDecimal avgSales;
        private boolean weekend;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class YearlyStat {
        private String label;          // "FY24", "FY27 exp."
        private BigDecimal sales;
        private boolean projected;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BranchStat {
        private Long branchId;
        private String name;
        private BigDecimal sales;
        private int bills;
    }
}
