import { useEffect, useState } from 'react';
import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import {
  useDiscountBudgets, useLoyaltySettings, useUpdateLoyaltySettings, useUpsertDiscountBudget,
} from '@/hooks/loyaltyHooks';
import type { DiscountBudgetDto, LoyaltySettingsDto } from '@/types/loyalty';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

// The numeric knobs, as [field, label, optional suffix] — rendered into a simple grid of inputs.
const NUMERIC_FIELDS: [keyof LoyaltySettingsDto, string, string?][] = [
  ['discountPctTarget', 'Discount budget target', '% of gross'],
  ['holdoutPct', 'Holdout group', '% of customers'],
  ['coinValue', 'Coin value', '₹ per coin'],
  ['maxCoinPctOfOrder', 'Max coins per order', '% of order'],
  ['minOrderForCoins', 'Min order to redeem coins', '₹'],
  ['coinExpiryMonths', 'Coin expiry', 'months'],
  ['scoreHalfLifeDays', 'Score decay half-life', 'days'],
  ['dailyCap', 'Non-purchase daily cap', 'points'],
  ['monthlyCap', 'Non-purchase monthly cap', 'points'],
  ['returnWindowDays', 'Return window', 'days'],
  ['newCodeCooldownDays', 'New-code cooldown', 'days'],
  ['returnRateBlockPct', 'Block above return rate', '%'],
  ['marginFloorPct', 'Gross-margin floor', '%'],
];

export default function LoyaltySettingsPage() {
  const { data: settings, isLoading } = useLoyaltySettings();
  const update = useUpdateLoyaltySettings();
  const [form, setForm] = useState<LoyaltySettingsDto | null>(null);

  useEffect(() => { if (settings) setForm(settings); }, [settings]);

  const patch = (p: Partial<LoyaltySettingsDto>) => setForm((f) => (f ? { ...f, ...p } : f));
  const save = () => { if (form) update.mutate(form); };

  if (isLoading || !form) return <p className="text-sm text-muted-foreground">Loading…</p>;

  return (
    <div className="space-y-6">
      {/* Master kill switch */}
      <Card>
        <CardHeader>
          <CardTitle>Discount programme</CardTitle>
          <CardDescription>
            Turn the discount/promotion side on or off for the whole site. When off, the Loyalty Score and
            tiers keep being calculated, but no discount codes or promotions are issued or emailed, and
            codes/coins cannot be redeemed at checkout.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-between rounded-md border p-4">
            <div>
              <Label htmlFor="discounts-enabled" className="text-base">
                {form.discountsEnabled ? 'Enabled' : 'Disabled'}
              </Label>
              <p className="text-sm text-muted-foreground">
                {form.discountsEnabled
                  ? 'Offers, promotion emails and checkout redemption are live.'
                  : 'Scoring continues; all cash discounts, codes and coin redemption are paused.'}
              </p>
            </div>
            <Switch
              id="discounts-enabled"
              data-testid="loyalty-discounts-enabled"
              checked={form.discountsEnabled}
              onCheckedChange={(v) => patch({ discountsEnabled: v })}
            />
          </div>
        </CardContent>
      </Card>

      {/* Numeric knobs */}
      <Card>
        <CardHeader>
          <CardTitle>Programme settings</CardTitle>
          <CardDescription>Budget, coins, scoring and guard thresholds.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {NUMERIC_FIELDS.map(([field, label, suffix]) => (
              <div className="grid gap-2" key={field}>
                <Label htmlFor={`s-${field}`}>{label}{suffix ? ` (${suffix})` : ''}</Label>
                <Input
                  id={`s-${field}`}
                  type="number"
                  step="any"
                  value={String(form[field] ?? '')}
                  onChange={(e) => patch({ [field]: e.target.value === '' ? 0 : Number(e.target.value) } as Partial<LoyaltySettingsDto>)}
                />
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      <div className="flex justify-end">
        <Button onClick={save} disabled={update.isPending} data-testid="loyalty-settings-save">
          {update.isPending ? 'Saving…' : 'Save settings'}
        </Button>
      </div>

      <BudgetsSection />
    </div>
  );
}

/** Monthly discount-budget list + upsert (the budget = discount% target × that month's sales target). */
function BudgetsSection() {
  const { data: budgets } = useDiscountBudgets();
  const upsert = useUpsertDiscountBudget();
  const [month, setMonth] = useState('');
  const [amount, setAmount] = useState('');

  const add = () => {
    if (!month || amount.trim() === '') return;
    upsert.mutate({ month, budgetAmount: Number(amount) }, { onSuccess: () => { setMonth(''); setAmount(''); } });
  };

  const columnDefs: ColDef<DiscountBudgetDto>[] = [
    { field: 'month', flex: 1 },
    { field: 'budgetAmount', headerName: 'Budget ₹', valueFormatter: (p) => `₹${Number(p.value ?? 0).toFixed(0)}`, flex: 1 },
    { field: 'spentToDate', headerName: 'Spent ₹', valueFormatter: (p) => `₹${Number(p.value ?? 0).toFixed(0)}`, flex: 1 },
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle>Monthly discount budget</CardTitle>
        <CardDescription>One row per month (yyyy-MM). Spend is tracked automatically as codes redeem.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="grid gap-2">
            <Label htmlFor="b-month">Month</Label>
            <Input id="b-month" type="month" value={month} onChange={(e) => setMonth(e.target.value)} className="w-44" />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="b-amount">Budget ₹</Label>
            <Input id="b-amount" type="number" value={amount} onChange={(e) => setAmount(e.target.value)} className="w-44" />
          </div>
          <Button onClick={add} disabled={upsert.isPending || !month || amount.trim() === ''}>Set budget</Button>
        </div>
        <AdminDataGrid<DiscountBudgetDto>
          rowData={budgets ?? []}
          columnDefs={columnDefs}
          emptyMessage="No budgets set."
          testId="loyalty-budgets-grid"
        />
      </CardContent>
    </Card>
  );
}
