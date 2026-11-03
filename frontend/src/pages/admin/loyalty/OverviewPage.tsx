import {
  Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import { useLoyaltyMetrics, useLoyaltySettings } from '@/hooks/loyaltyHooks';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

const TIER_COLORS: Record<string, string> = {
  BRONZE: '#b08d57', SILVER: '#9ca3af', GOLD: '#eab308', PLATINUM: '#6366f1',
};

/**
 * Loyalty metrics dashboard — the lines that feed the growth tracker (Discount %, Repeat %, incremental
 * revenue). The programme is judged against the holdout, not raw redemptions.
 */
export default function LoyaltyOverviewPage() {
  const { data: settings } = useLoyaltySettings();
  const { data: m, isLoading } = useLoyaltyMetrics();

  if (isLoading || !m) return <p className="text-sm text-muted-foreground">Loading…</p>;

  const repeatLift = (m.holdoutComparison.memberRepeatRate - m.holdoutComparison.holdoutRepeatRate) * 100;
  const holdoutData = [
    { name: 'Members', repeat: +(m.holdoutComparison.memberRepeatRate * 100).toFixed(1), revenue: Math.round(m.holdoutComparison.memberAvgRevenue) },
    { name: 'Holdout', repeat: +(m.holdoutComparison.holdoutRepeatRate * 100).toFixed(1), revenue: Math.round(m.holdoutComparison.holdoutAvgRevenue) },
  ];

  return (
    <div className="space-y-6">
      {settings && !settings.discountsEnabled && (
        <div className="rounded-md border border-amber-500/40 bg-amber-500/10 px-4 py-3 text-sm" data-testid="loyalty-disabled-banner">
          The discount programme is <strong>off</strong>. Loyalty scoring and tiers keep running, but no codes
          or promotions are issued and redemption is paused. Re-enable it under Settings.
        </div>
      )}

      {/* Headline KPIs */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Stat title="Members" value={String(m.members)} sub={`${m.holdout} in holdout`} />
        <Stat
          title="Discount % of gross"
          value={`${m.budget.discountPctOfGross}%`}
          sub={`target ≤ ${m.budget.targetPct}%`}
          danger={m.budget.discountPctOfGross > m.budget.targetPct}
        />
        <Stat title="Budget used" value={`${m.budget.pctOfBudgetUsed}%`} sub={`₹${Math.round(m.budget.spentToDate)} / ₹${Math.round(m.budget.budgetAmount)}`} />
        <Stat title="Coin liability" value={`₹${Math.round(m.coinLiability)}`} sub="unredeemed coins" />
      </div>

      {/* Incremental vs holdout */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader><CardTitle className="text-base">Members vs holdout</CardTitle></CardHeader>
          <CardContent>
            <p className="mb-3 text-sm text-muted-foreground">
              Repeat rate is <strong className={repeatLift >= 0 ? 'text-green-600' : 'text-destructive'}>{repeatLift >= 0 ? '+' : ''}{repeatLift.toFixed(1)} pts</strong> and incremental revenue is
              <strong> ₹{Math.round(m.holdoutComparison.incrementalRevenuePerCustomer)}</strong> per customer vs the holdout.
            </p>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={holdoutData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" />
                <YAxis yAxisId="l" />
                <YAxis yAxisId="r" orientation="right" />
                <Tooltip />
                <Legend />
                <Bar yAxisId="l" dataKey="repeat" name="Repeat %" fill="#6366f1" radius={[4, 4, 0, 0]} />
                <Bar yAxisId="r" dataKey="revenue" name="Avg revenue ₹" fill="#22c55e" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle className="text-base">Tier mix</CardTitle></CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie data={m.tierMix} dataKey="count" nameKey="tier" cx="50%" cy="50%" outerRadius={80} label>
                  {m.tierMix.map((t) => <Cell key={t.tier} fill={TIER_COLORS[t.tier] ?? '#94a3b8'} />)}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      </div>

      {/* Redemption by trigger */}
      <Card>
        <CardHeader><CardTitle className="text-base">Codes by trigger</CardTitle></CardHeader>
        <CardContent>
          {m.redemptionByTrigger.length === 0 ? (
            <p className="text-sm text-muted-foreground">No codes issued yet.</p>
          ) : (
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={m.redemptionByTrigger}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="trigger" angle={-20} textAnchor="end" height={60} interval={0} fontSize={11} />
                <YAxis allowDecimals={false} />
                <Tooltip />
                <Legend />
                <Bar dataKey="issued" name="Issued" fill="#94a3b8" radius={[4, 4, 0, 0]} />
                <Bar dataKey="redeemed" name="Redeemed" fill="#6366f1" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function Stat({ title, value, sub, danger }: { title: string; value: string; sub?: string; danger?: boolean }) {
  return (
    <Card>
      <CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-muted-foreground">{title}</CardTitle></CardHeader>
      <CardContent>
        <div className={`text-3xl font-bold ${danger ? 'text-destructive' : ''}`}>{value}</div>
        {sub && <div className="text-xs text-muted-foreground">{sub}</div>}
      </CardContent>
    </Card>
  );
}
