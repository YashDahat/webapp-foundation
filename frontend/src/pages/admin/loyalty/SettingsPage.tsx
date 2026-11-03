import { useState } from 'react';
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
import { ConfirmDialog } from '@/components/ConfirmDialog';

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
  // Working copy of the numeric knobs while the edit panel is open; null = read-only view.
  const [form, setForm] = useState<LoyaltySettingsDto | null>(null);
  // The value the admin is about to flip the kill switch to — drives the confirm dialog.
  // null = no pending change; the Switch stays on the saved value until they confirm.
  const [pendingToggle, setPendingToggle] = useState<boolean | null>(null);

  const patch = (p: Partial<LoyaltySettingsDto>) => setForm((f) => (f ? { ...f, ...p } : f));

  if (isLoading || !settings) return <p className="text-sm text-muted-foreground">Loading…</p>;

  const startEdit = () => setForm({ ...settings });
  const cancelEdit = () => setForm(null);
  // Persist only the knobs; keep the live kill-switch value from the server (it saves on its own).
  const saveKnobs = () => {
    if (form) update.mutate({ ...form, discountsEnabled: settings.discountsEnabled }, { onSuccess: () => setForm(null) });
  };
  // The kill switch persists immediately once confirmed, independent of the edit panel.
  const confirmToggle = () => {
    if (pendingToggle != null) update.mutate({ ...settings, discountsEnabled: pendingToggle });
    setPendingToggle(null);
  };

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
                {settings.discountsEnabled ? 'Enabled' : 'Disabled'}
              </Label>
              <p className="text-sm text-muted-foreground">
                {settings.discountsEnabled
                  ? 'Offers, promotion emails and checkout redemption are live.'
                  : 'Scoring continues; all cash discounts, codes and coin redemption are paused.'}
              </p>
            </div>
            <Switch
              id="discounts-enabled"
              data-testid="loyalty-discounts-enabled"
              checked={settings.discountsEnabled}
              disabled={update.isPending}
              onCheckedChange={(v) => setPendingToggle(v)}
            />
          </div>
        </CardContent>
      </Card>

      {/* Confirm before flipping the kill switch — it materially moves sales, so make it deliberate. */}
      <ConfirmDialog
        open={pendingToggle != null}
        onOpenChange={() => setPendingToggle(null)}
        title={pendingToggle ? 'Enable the discount programme?' : 'Disable the discount programme?'}
        description={
          pendingToggle ? (
            <>
              This turns discount code generation and redemption <strong>back on</strong> for the whole
              site. Customers feel rewarded for buying from you, so this typically lifts repeat orders and
              your overall sales numbers. Offers and promotion emails will resume issuing.
            </>
          ) : (
            <>
              This turns discount code generation and redemption <strong>off</strong> for the whole site.
              Discounts make customers feel rewarded for buying from you, so switching them off can reduce
              repeat orders and your overall sales numbers. No new codes or promotions will be issued or
              emailed, and existing codes and coins can’t be redeemed at checkout. (Scoring and tiers keep
              running.)
            </>
          )
        }
        confirmLabel={pendingToggle ? 'Enable discounts' : 'Disable discounts'}
        destructive={!pendingToggle}
        onConfirm={confirmToggle}
      />

      {/* Numeric knobs — read-only until the admin clicks Edit settings. */}
      <Card>
        <CardHeader className="flex flex-row items-start justify-between gap-4">
          <div className="space-y-1.5">
            <CardTitle>Programme settings</CardTitle>
            <CardDescription>Budget, coins, scoring and guard thresholds.</CardDescription>
          </div>
          {!form && (
            <Button variant="outline" onClick={startEdit} data-testid="loyalty-settings-edit">
              Edit settings
            </Button>
          )}
        </CardHeader>
        <CardContent className="space-y-4">
          {form && (
            <div className="flex gap-2">
              <Button onClick={saveKnobs} disabled={update.isPending} data-testid="loyalty-settings-save">
                {update.isPending ? 'Saving…' : 'Save settings'}
              </Button>
              <Button variant="outline" onClick={cancelEdit} disabled={update.isPending}>
                Cancel
              </Button>
            </div>
          )}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {NUMERIC_FIELDS.map(([field, label, suffix]) => (
              <div className="grid gap-2" key={field}>
                <Label htmlFor={`s-${field}`}>{label}{suffix ? ` (${suffix})` : ''}</Label>
                {form ? (
                  <Input
                    id={`s-${field}`}
                    type="number"
                    step="any"
                    value={String(form[field] ?? '')}
                    onChange={(e) => patch({ [field]: e.target.value === '' ? 0 : Number(e.target.value) } as Partial<LoyaltySettingsDto>)}
                  />
                ) : (
                  <p id={`s-${field}`} className="text-sm font-medium">{String(settings[field] ?? '—')}</p>
                )}
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

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
