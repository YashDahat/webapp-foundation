// FOUNDATION-owned admin Analytics page. Two tabs: the Dashboard (orders-derived KPIs, weekday-aware
// targets, projection, attention list) and the end-of-day Daily log (COGS / expenses / complaints).
import { useState } from 'react';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Button } from '@/components/ui/button';
import { AnalyticsDashboard } from '@/components/admin/analytics/AnalyticsDashboard';
import { DailyLogManager } from '@/components/admin/analytics/DailyLogManager';
import { useAnalyticsSummary } from '@/hooks/analyticsHooks';
import { useBranches } from '@/hooks/branchHooks';
import type { AnalyticsPeriod } from '@/types/analytics';

const PERIODS: { key: AnalyticsPeriod; label: string }[] = [
  { key: 'TODAY', label: 'Today' },
  { key: 'WEEK', label: 'This week' },
  { key: 'MONTH', label: 'This month' },
  { key: 'YEAR', label: 'This year' },
];

export default function AdminAnalyticsPage() {
  const [period, setPeriod] = useState<AnalyticsPeriod>('MONTH');
  const [branchId, setBranchId] = useState<number | null>(null);
  const { data: summary, isLoading, isError } = useAnalyticsSummary(period, branchId);
  const { data: branches } = useBranches();

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">Analytics</h1>
          {summary && (
            <p className="text-sm text-muted-foreground">
              {summary.outletCount} outlet{summary.outletCount === 1 ? '' : 's'} · updated {summary.asOf}
            </p>
          )}
        </div>
      </div>

      <Tabs defaultValue="dashboard">
        <TabsList>
          <TabsTrigger value="dashboard">Dashboard</TabsTrigger>
          <TabsTrigger value="daily-log">Daily log</TabsTrigger>
        </TabsList>

        <TabsContent value="dashboard" className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="inline-flex rounded-md border p-0.5">
              {PERIODS.map((p) => (
                <Button
                  key={p.key}
                  size="sm"
                  variant={period === p.key ? 'default' : 'ghost'}
                  onClick={() => setPeriod(p.key)}
                >
                  {p.label}
                </Button>
              ))}
            </div>
            {branches && branches.length > 1 && (
              <select
                className="h-9 rounded-md border bg-background px-3 text-sm"
                value={branchId ?? ''}
                onChange={(e) => setBranchId(e.target.value === '' ? null : Number(e.target.value))}
              >
                <option value="">All outlets</option>
                {branches.map((b) => (
                  <option key={b.id} value={b.id}>{b.name}</option>
                ))}
              </select>
            )}
          </div>

          {isLoading && <p className="text-sm text-muted-foreground">Loading…</p>}
          {isError && <p className="text-sm text-red-600">Could not load analytics.</p>}
          {summary && <AnalyticsDashboard summary={summary} />}
        </TabsContent>

        <TabsContent value="daily-log">
          <DailyLogManager />
        </TabsContent>
      </Tabs>
    </div>
  );
}
