import { useState } from 'react';
import { Pencil } from 'lucide-react';
import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { type RowAction } from '@/components/admin/RowActionsCell';
import { StepperSheet, type WizardStep } from '@/components/admin/StepperSheet';
import { useLoyaltyEventRules, useUpdateLoyaltyEventRule } from '@/hooks/loyaltyHooks';
import type { LoyaltyEventRuleDto } from '@/types/loyalty';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';

interface RuleForm {
  id: number;
  eventType: string;
  points: string;
  perDayCap: string;
  onceOnly: boolean;
  earnsCoins: boolean;
}

const toForm = (r: LoyaltyEventRuleDto): RuleForm => ({
  id: r.id, eventType: r.eventType, points: String(r.points),
  perDayCap: r.perDayCap != null ? String(r.perDayCap) : '', onceOnly: r.onceOnly, earnsCoins: r.earnsCoins,
});

/** The Loyalty Score scoring table: points, daily cap, once-only and earns-coins per tracked action. */
export default function LoyaltyScoringPage() {
  const { data: rules, isLoading } = useLoyaltyEventRules();
  const update = useUpdateLoyaltyEventRule();
  const [form, setForm] = useState<RuleForm | null>(null);
  const patch = (p: Partial<RuleForm>) => setForm((f) => (f ? { ...f, ...p } : f));

  const save = () => {
    if (!form) return;
    update.mutate(
      { id: form.id, req: { points: Number(form.points), perDayCap: form.perDayCap.trim() === '' ? null : Number(form.perDayCap), onceOnly: form.onceOnly, earnsCoins: form.earnsCoins } },
      { onSuccess: () => setForm(null) },
    );
  };

  const columnDefs: ColDef<LoyaltyEventRuleDto>[] = [
    { field: 'eventType', headerName: 'Action', flex: 1.3 },
    { field: 'points', maxWidth: 110 },
    { field: 'perDayCap', headerName: 'Daily cap', valueFormatter: (p) => (p.value == null ? '—' : String(p.value)), maxWidth: 130 },
    { field: 'onceOnly', headerName: 'Once only', valueFormatter: (p) => (p.value ? 'Yes' : 'No'), maxWidth: 120 },
    { field: 'earnsCoins', headerName: 'Earns coins', valueFormatter: (p) => (p.value ? 'Yes' : 'No'), maxWidth: 130 },
  ];

  const actions: RowAction<LoyaltyEventRuleDto>[] = [
    { label: 'Edit', icon: Pencil, onClick: (row) => setForm(toForm(row)) },
  ];

  const steps: WizardStep[] = form ? [
    {
      id: 'rule',
      title: form.eventType,
      description: 'Points & limits',
      content: (
        <>
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-2">
              <Label htmlFor="r-points">Points</Label>
              <Input id="r-points" type="number" step="0.1" value={form.points} onChange={(e) => patch({ points: e.target.value })} />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="r-cap">Daily cap</Label>
              <Input id="r-cap" type="number" placeholder="none" value={form.perDayCap} onChange={(e) => patch({ perDayCap: e.target.value })} />
            </div>
          </div>
          <div className="flex items-center justify-between">
            <Label htmlFor="r-once">Once only (ever)</Label>
            <Switch id="r-once" checked={form.onceOnly} onCheckedChange={(v) => patch({ onceOnly: v })} />
          </div>
          <div className="flex items-center justify-between">
            <Label htmlFor="r-coins">Also earns coins</Label>
            <Switch id="r-coins" checked={form.earnsCoins} onCheckedChange={(v) => patch({ earnsCoins: v })} />
          </div>
        </>
      ),
    },
  ] : [];

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        For <span className="font-medium">PURCHASE</span>, points are per ₹1,000 paid. Non-purchase actions
        share a combined daily/monthly cap (set under Settings).
      </p>
      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <AdminDataGrid<LoyaltyEventRuleDto>
          rowData={rules ?? []}
          columnDefs={columnDefs}
          actions={actions}
          emptyMessage="No scoring rules configured."
          testId="loyalty-scoring-grid"
        />
      )}
      <StepperSheet
        open={form != null}
        onOpenChange={(next) => !next && setForm(null)}
        title={form ? `Edit rule — ${form.eventType}` : ''}
        steps={steps}
        canSave={!!form && form.points.trim() !== ''}
        saving={update.isPending}
        onSave={save}
      />
    </div>
  );
}
