// FOUNDATION-owned analytics types (mirror backend AnalyticsSummaryDto / DailyBusinessLog*).
// Sales/bills/customers are derived from paid orders; profit/margin/complaints fold in the daily log.
// Percentage fields are FRACTIONS (0.19 = +19%); the UI multiplies by 100.

export type AnalyticsPeriod = 'TODAY' | 'WEEK' | 'MONTH' | 'YEAR';
export type Severity = 'ACT_NOW' | 'WATCH' | 'OK';
export type Direction = 'UP' | 'DOWN' | 'FLAT' | 'NONE';

export interface Banner {
  growing: boolean;
  headline: string;
  subline: string;
  onTrack: number;
  toWatch: number;
  needAction: number;
  totalTracked: number;
}

export interface KpiCard {
  key: string;
  label: string;
  value: number | null;
  display: string;
  changePct: number | null;
  direction: Direction;
  comparisonLabel: string | null;
  note: string | null;
}

export interface TargetProgress {
  actual: number;
  target: number | null;
  achievedPct: number;
  periodLabel: string;
}

export interface CumulativePoint {
  label: string;
  actual: number | null;
  projected: number | null;
  goal: number | null;
  lastYear: number | null;
}

export interface Projection {
  actualSoFar: number;
  expectedSales: number;
  goal: number | null;
  lastYearSales: number | null;
  salesChangePct: number | null;
  expectedProfit: number | null;
  profitChangePct: number | null;
  yearGoalDonePct: number;
  monthsGone: number;
  series: CumulativePoint[];
}

export interface DayBar {
  date: string;
  dayOfMonth: number;
  sales: number;
  target: number | null;
  met: boolean;
  future: boolean;
}

export interface DayByDay {
  soldSoFar: number;
  monthTarget: number | null;
  expectedMonthEnd: number;
  monthAchievedPct: number;
  daysMet: number;
  daysElapsed: number;
  bars: DayBar[];
}

export interface AttentionItem {
  severity: Severity;
  title: string;
  detail: string;
  action: string;
}

export interface WeekdayStat {
  day: string;
  avgSales: number;
  weekend: boolean;
}

export interface YearlyStat {
  label: string;
  sales: number;
  projected: boolean;
}

export interface BranchStat {
  branchId: number | null;
  name: string;
  sales: number;
  bills: number;
}

export interface AnalyticsSummary {
  period: AnalyticsPeriod;
  branchId: number | null;
  asOf: string;
  currency: string;
  outletCount: number;
  banner: Banner;
  kpis: KpiCard[];
  targetProgress: TargetProgress;
  projection: Projection;
  dayByDay: DayByDay;
  attention: AttentionItem[];
  weekdays: WeekdayStat[];
  yearly: YearlyStat[];
  branches: BranchStat[];
}

export interface DailyBusinessLogDto {
  id: number;
  logDate: string;
  cogs: number;
  operatingExpenses: number;
  complaints: number;
  note?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface UpsertDailyBusinessLogRequest {
  logDate: string;
  cogs?: number | null;
  operatingExpenses?: number | null;
  complaints?: number | null;
  note?: string | null;
}
