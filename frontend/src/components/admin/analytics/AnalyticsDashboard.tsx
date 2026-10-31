// FOUNDATION-owned Analytics dashboard: the six screen sections (banner → KPI cards → year
// projection → day-by-day → attention list → weekday/year-history + per-branch). Pure presentation
// over the AnalyticsSummary payload. Percentage fields are fractions (×100 for display).
import {
  Bar,
  BarChart,
  Cell,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useTheme } from '@/components/theme/ThemeProvider';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import type { AnalyticsSummary, KpiCard, Severity } from '@/types/analytics';

// Theme-aware palettes. "ink" is the primary foreground (black in light, near-white in dark) used for
// the actual/on-target series; "red" is lightened in dark for contrast; "faint" is the future/empty bar.
const LIGHT = { ink: '#111827', red: '#dc2626', grey: '#9ca3af', faint: '#e5e7eb', axis: '#6b7280', tipBg: '#ffffff', tipBorder: '#e5e7eb', tipText: '#111827', cursor: 'rgba(0,0,0,0.04)' };
const DARK = { ink: '#f3f4f6', red: '#f87171', grey: '#9ca3af', faint: '#374151', axis: '#9ca3af', tipBg: '#1f2937', tipBorder: '#374151', tipText: '#f3f4f6', cursor: 'rgba(255,255,255,0.06)' };

const pct = (f: number | null | undefined) => (f == null ? '—' : `${Math.round(f * 100)}%`);
const signedPct = (f: number | null | undefined) =>
  f == null ? 'no prior data' : `${f >= 0 ? '+' : ''}${Math.round(f * 100)}%`;

/** Compact ₹ axis label: Cr / L / plain. */
function axisMoney(v: number): string {
  if (Math.abs(v) >= 1e7) return `₹${(v / 1e7).toFixed(1)}Cr`;
  if (Math.abs(v) >= 1e5) return `₹${Math.round(v / 1e5)}L`;
  return `₹${v}`;
}

export function AnalyticsDashboard({ summary }: { summary: AnalyticsSummary }): React.JSX.Element {
  const { banner, kpis, targetProgress, projection, dayByDay, attention, weekdays, yearly, branches } = summary;
  const { resolvedTheme } = useTheme();
  const c = resolvedTheme === 'dark' ? DARK : LIGHT;

  // shared chart props
  const tick = { fontSize: 12, fill: c.axis };
  const tickSm = { fontSize: 11, fill: c.axis };
  const tickXs = { fontSize: 10, fill: c.axis };
  const tt = {
    contentStyle: { backgroundColor: c.tipBg, border: `1px solid ${c.tipBorder}`, borderRadius: 8, color: c.tipText },
    labelStyle: { color: c.tipText },
    itemStyle: { color: c.tipText },
    cursor: { fill: c.cursor },
  };

  return (
    <div className="space-y-6">
      {/* 2.1 — the short answer */}
      <Card className="overflow-hidden border-0 bg-neutral-900 p-6 text-neutral-50 md:p-8">
        <p className="text-xs font-semibold uppercase tracking-widest text-neutral-400">The short answer</p>
        <div className="mt-3 flex flex-col gap-6 md:flex-row md:items-end md:justify-between">
          <div className="max-w-2xl">
            <h2 className="text-2xl font-bold leading-snug md:text-3xl">{banner.headline}</h2>
            <p className="mt-2 text-sm text-neutral-300">{banner.subline}</p>
          </div>
          <div className="flex gap-6">
            <Count n={banner.onTrack} label="on track" tone="ink" />
            <Count n={banner.toWatch} label="to watch" tone="amber" />
            <Count n={banner.needAction} label="need action" tone="red" />
          </div>
        </div>
      </Card>

      {/* 2.2 — how we are doing */}
      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {kpis.map((k) => <KpiTile key={k.key} kpi={k} />)}
      </section>
      <TargetBar
        actual={targetProgress.actual}
        achieved={targetProgress.achievedPct}
        label={targetProgress.periodLabel}
        hasTarget={targetProgress.target != null}
      />

      {/* 2.3 — where the year is heading */}
      <Card className="p-5">
        <SectionTitle title="Where the year is heading" subtitle="Total sales added up month by month (₹)" />
        <div className="mt-4 h-72 w-full">
          <ResponsiveContainer>
            <ComposedChart data={projection.series} margin={{ top: 10, right: 16, bottom: 0, left: 0 }}>
              <XAxis dataKey="label" tick={tick} stroke={c.axis} />
              <YAxis tickFormatter={axisMoney} tick={tickSm} width={56} stroke={c.axis} />
              <Tooltip {...tt} formatter={(v) => axisMoney(Number(v ?? 0))} />
              {projection.series.some((p) => p.lastYear != null) && (
                <Line type="monotone" dataKey="lastYear" stroke={c.grey} strokeWidth={2} dot={false} name="Last year" />
              )}
              {projection.series.some((p) => p.goal != null) && (
                <Line type="monotone" dataKey="goal" stroke={c.red} strokeWidth={2} dot={false} name="Goal" />
              )}
              <Line type="monotone" dataKey="projected" stroke={c.ink} strokeWidth={2} strokeDasharray="5 4" dot={false} name="Expected at pace" />
              <Line type="monotone" dataKey="actual" stroke={c.ink} strokeWidth={3} dot={false} name="Actual" connectNulls={false} />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
        <div className="mt-4 grid gap-4 border-t pt-4 sm:grid-cols-3">
          <Stat big label="Expected sales this year" value={fmtMoney(projection.expectedSales)}
            sub={projection.salesChangePct == null ? 'No prior year yet' : `${signedPct(projection.salesChangePct)} vs last year`} up={(projection.salesChangePct ?? 0) >= 0} />
          <Stat big label="Expected profit this year"
            value={projection.expectedProfit == null ? 'No cost data' : fmtMoney(projection.expectedProfit)}
            sub={projection.goal != null ? `Goal ${fmtMoney(projection.goal)}` : 'Set a goal to track'} />
          <div>
            <p className="text-xs text-muted-foreground">Year goal done</p>
            <p className="mt-1 text-sm font-medium">
              {projection.goal == null ? 'No goal set' : pct(projection.yearGoalDonePct)}
              <span className="text-muted-foreground"> · {projection.monthsGone} of 12 months gone</span>
            </p>
            {projection.goal != null && (
              <div className="mt-2 h-2 w-full rounded bg-muted">
                <div className="h-2 rounded bg-foreground" style={{ width: `${Math.min(100, projection.yearGoalDonePct * 100)}%` }} />
              </div>
            )}
          </div>
        </div>
      </Card>

      {/* 2.4 — day by day (current month) */}
      <Card className="p-5">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <SectionTitle title="This month, day by day" subtitle="Each bar is a day; the tick is that day's target. Red bars missed it." />
          <div className="flex gap-6 text-right">
            <div>
              <p className="text-xs text-muted-foreground">Sold so far</p>
              <p className="text-lg font-bold">{fmtMoney(dayByDay.soldSoFar)}
                {dayByDay.monthTarget != null && <span className="text-sm font-normal text-muted-foreground"> of {fmtMoney(dayByDay.monthTarget)}</span>}</p>
            </div>
            <div>
              <p className="text-xs text-muted-foreground">Expected month end</p>
              <p className="text-lg font-bold">{fmtMoney(dayByDay.expectedMonthEnd)}
                {dayByDay.monthTarget != null && <span className="ml-1 text-sm font-semibold text-red-600 dark:text-red-400">{pct(dayByDay.monthAchievedPct)}</span>}</p>
            </div>
          </div>
        </div>
        <div className="mt-4 h-60 w-full">
          <ResponsiveContainer>
            <BarChart data={dayByDay.bars} margin={{ top: 10, right: 8, bottom: 0, left: 0 }}>
              <XAxis dataKey="dayOfMonth" tick={tickXs} interval={2} stroke={c.axis} />
              <YAxis tickFormatter={axisMoney} tick={tickSm} width={48} stroke={c.axis} />
              <Tooltip {...tt} formatter={(v) => axisMoney(Number(v ?? 0))} labelFormatter={(l) => `Day ${l}`} />
              <Bar dataKey="sales" radius={[2, 2, 0, 0]}>
                {dayByDay.bars.map((b, i) => (
                  <Cell key={i} fill={b.future ? c.faint : b.met ? c.ink : c.red} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
        <p className="mt-2 text-sm text-muted-foreground">
          Target met on {dayByDay.daysMet} of {dayByDay.daysElapsed} days this month.
        </p>
      </Card>

      {/* 2.5 — what needs attention */}
      <Card className="p-5">
        <SectionTitle title="What needs attention" subtitle="Fix the red items first, one at a time, with one owner and a date." />
        <div className="mt-4 divide-y">
          {attention.length === 0 && (
            <p className="py-6 text-sm text-muted-foreground">Everything we track is on target. 🎉</p>
          )}
          {attention.map((a, i) => (
            <div key={i} className="grid gap-3 py-4 md:grid-cols-[110px_1fr_1.2fr] md:items-start">
              <SeverityBadge severity={a.severity} />
              <div>
                <p className="font-semibold">{a.title}</p>
                <p className="text-sm text-muted-foreground">{a.detail}</p>
              </div>
              <p className="text-sm text-muted-foreground">{a.action}</p>
            </div>
          ))}
        </div>
      </Card>

      {/* 2.6 — which days make money / six years of growth / branches */}
      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="p-5">
          <SectionTitle title="Which days make the money" subtitle="Average sales per day this year" />
          <div className="mt-4 h-64 w-full">
            <ResponsiveContainer>
              <BarChart data={weekdays} layout="vertical" margin={{ top: 4, right: 32, bottom: 0, left: 8 }}>
                <XAxis type="number" tickFormatter={axisMoney} tick={tickSm} stroke={c.axis} />
                <YAxis type="category" dataKey="day" tick={tick} width={40} stroke={c.axis} />
                <Tooltip {...tt} formatter={(v) => axisMoney(Number(v ?? 0))} />
                <Bar dataKey="avgSales" radius={[0, 2, 2, 0]}>
                  {weekdays.map((w, i) => <Cell key={i} fill={w.weekend ? c.ink : c.grey} />)}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>

        <Card className="p-5">
          <SectionTitle title="Years of growth" subtitle="Yearly sales (₹). The striped bar is this year, expected." />
          <div className="mt-4 h-64 w-full">
            <ResponsiveContainer>
              <BarChart data={yearly} margin={{ top: 16, right: 8, bottom: 0, left: 0 }}>
                <XAxis dataKey="label" tick={tickSm} stroke={c.axis} />
                <YAxis tickFormatter={axisMoney} tick={tickSm} width={52} stroke={c.axis} />
                <Tooltip {...tt} formatter={(v) => axisMoney(Number(v ?? 0))} />
                <Bar dataKey="sales" radius={[2, 2, 0, 0]}>
                  {yearly.map((y, i) => <Cell key={i} fill={y.projected ? c.grey : c.ink} />)}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </div>

      {branches.length > 1 && (
        <Card className="p-5">
          <SectionTitle title="By outlet" subtitle="Sales this period, per branch" />
          <div className="mt-4 h-56 w-full">
            <ResponsiveContainer>
              <BarChart data={branches} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
                <XAxis dataKey="name" tick={tickSm} stroke={c.axis} />
                <YAxis tickFormatter={axisMoney} tick={tickSm} width={52} stroke={c.axis} />
                <Tooltip {...tt} formatter={(v) => axisMoney(Number(v ?? 0))} />
                <Bar dataKey="sales" fill={c.ink} radius={[2, 2, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
      )}
    </div>
  );
}

// ── small building blocks ──

function fmtMoney(v: number | null): string {
  if (v == null) return '—';
  const abs = Math.abs(v);
  const sign = v < 0 ? '-' : '';
  if (abs >= 1e7) return `${sign}₹${(abs / 1e7).toFixed(1).replace(/\.0$/, '')} Cr`;
  if (abs >= 1e5) return `${sign}₹${(abs / 1e5).toFixed(1).replace(/\.0$/, '')} L`;
  return `${sign}₹${Math.round(abs).toLocaleString('en-IN')}`;
}

function Count({ n, label, tone }: { n: number; label: string; tone: 'ink' | 'amber' | 'red' }) {
  const color = tone === 'red' ? 'text-red-500' : tone === 'amber' ? 'text-amber-400' : 'text-neutral-50';
  return (
    <div className="text-center">
      <p className={`text-4xl font-bold ${color}`}>{n}</p>
      <p className="mt-1 text-xs text-neutral-400">{label}</p>
    </div>
  );
}

function KpiTile({ kpi }: { kpi: KpiCard }) {
  const up = kpi.direction === 'UP';
  const down = kpi.direction === 'DOWN';
  return (
    <Card className="p-4">
      <p className="text-xs text-muted-foreground">{kpi.label}</p>
      <p className="mt-1 text-2xl font-bold">{kpi.display}</p>
      <p className="mt-1 text-sm">
        {kpi.direction === 'NONE' || kpi.changePct == null ? (
          <span className="text-muted-foreground">No comparison yet</span>
        ) : (
          <span className={down ? 'text-red-600 dark:text-red-400' : 'text-foreground'}>
            {up ? '▲' : down ? '▼' : '■'} {signedPct(kpi.changePct)} {kpi.comparisonLabel}
          </span>
        )}
      </p>
      {kpi.note && <p className="mt-0.5 text-xs text-muted-foreground">{kpi.note}</p>}
    </Card>
  );
}

function TargetBar({ actual, achieved, label, hasTarget }: { actual: number; achieved: number; label: string; hasTarget: boolean }) {
  const metAll = achieved >= 1;
  return (
    <Card className="p-4">
      <div className="flex items-center justify-between text-sm">
        <span className="text-muted-foreground">{label}</span>
        <span className="font-semibold">{hasTarget ? `${pct(achieved)} of target` : 'No target history yet'}</span>
      </div>
      <div className="mt-2 h-3 w-full rounded bg-muted">
        <div className={`h-3 rounded ${metAll ? 'bg-foreground' : 'bg-red-600 dark:bg-red-500'}`}
          style={{ width: `${Math.min(100, (hasTarget ? achieved : 0) * 100)}%` }} />
      </div>
    </Card>
  );
}

function SectionTitle({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <div>
      <h3 className="text-lg font-bold">{title}</h3>
      {subtitle && <p className="text-sm text-muted-foreground">{subtitle}</p>}
    </div>
  );
}

function Stat({ label, value, sub, big, up }: { label: string; value: string; sub?: string; big?: boolean; up?: boolean }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className={big ? 'text-2xl font-bold' : 'text-lg font-semibold'}>{value}</p>
      {sub && <p className={`text-xs ${up === false ? 'text-red-600 dark:text-red-400' : 'text-muted-foreground'}`}>{sub}</p>}
    </div>
  );
}

function SeverityBadge({ severity }: { severity: Severity }) {
  if (severity === 'ACT_NOW') return <Badge className="w-fit bg-red-600 text-white hover:bg-red-600">ACT NOW</Badge>;
  if (severity === 'WATCH') return <Badge variant="outline" className="w-fit border-red-500 text-red-600 dark:text-red-400">WATCH</Badge>;
  return <Badge variant="outline" className="w-fit">OK</Badge>;
}
