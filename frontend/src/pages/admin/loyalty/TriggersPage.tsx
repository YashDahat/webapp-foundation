import { useState } from 'react';
import { Pencil } from 'lucide-react';
import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { type RowAction } from '@/components/admin/RowActionsCell';
import { StepperSheet, type WizardStep } from '@/components/admin/StepperSheet';
import { useLoyaltyTriggers, useUpdateLoyaltyTrigger } from '@/hooks/loyaltyHooks';
import type { LoyaltyTriggerConfigDto } from '@/types/loyalty';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Textarea } from '@/components/ui/textarea';

interface TriggerForm {
  id: number;
  trigger: string;
  enabled: boolean;
  expiryDays: string;
  rungsJson: string;
}

const toForm = (t: LoyaltyTriggerConfigDto): TriggerForm => ({
  id: t.id, trigger: t.trigger, enabled: t.enabled, expiryDays: String(t.expiryDays), rungsJson: t.rungsJson ?? '',
});

/** Enable/disable each offer trigger and tune its offer ladder (cheapest rung first) + code expiry. */
export default function LoyaltyTriggersPage() {
  const { data: triggers, isLoading } = useLoyaltyTriggers();
  const update = useUpdateLoyaltyTrigger();
  const [form, setForm] = useState<TriggerForm | null>(null);
  const patch = (p: Partial<TriggerForm>) => setForm((f) => (f ? { ...f, ...p } : f));

  const jsonValid = (() => {
    if (!form || form.rungsJson.trim() === '') return true;
    try { JSON.parse(form.rungsJson); return true; } catch { return false; }
  })();

  const save = () => {
    if (!form || !jsonValid) return;
    update.mutate(
      { id: form.id, req: { enabled: form.enabled, expiryDays: Number(form.expiryDays), rungsJson: form.rungsJson.trim() || null } },
      { onSuccess: () => setForm(null) },
    );
  };

  const columnDefs: ColDef<LoyaltyTriggerConfigDto>[] = [
    { field: 'trigger', flex: 1 },
    { field: 'enabled', valueFormatter: (p) => (p.value ? 'On' : 'Off'), maxWidth: 110 },
    { field: 'expiryDays', headerName: 'Expiry (days)', maxWidth: 150 },
    { field: 'rungsJson', headerName: 'Offer ladder', flex: 2 },
  ];

  const actions: RowAction<LoyaltyTriggerConfigDto>[] = [
    { label: 'Edit', icon: Pencil, onClick: (row) => setForm(toForm(row)) },
  ];

  const steps: WizardStep[] = form ? [
    {
      id: 'trigger',
      title: form.trigger,
      description: 'Ladder & expiry',
      error: !jsonValid,
      content: (
        <>
          <div className="flex items-center justify-between">
            <Label htmlFor="tr-enabled">Enabled</Label>
            <Switch id="tr-enabled" checked={form.enabled} onCheckedChange={(v) => patch({ enabled: v })} />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="tr-expiry">Code expiry (days)</Label>
            <Input id="tr-expiry" type="number" value={form.expiryDays} onChange={(e) => patch({ expiryDays: e.target.value })} />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="tr-rungs">Offer ladder (JSON, cheapest first)</Label>
            <Textarea id="tr-rungs" rows={4} className="font-mono text-xs" value={form.rungsJson} onChange={(e) => patch({ rungsJson: e.target.value })} />
            {!jsonValid && <p className="text-xs text-destructive">Not valid JSON.</p>}
            <p className="text-xs text-muted-foreground">
              e.g. <code>[{'{'}"type":"FREE_SHIP"{'}'},{'{'}"type":"PCT","value":10,"maxDiscount":300{'}'}]</code>
            </p>
          </div>
        </>
      ),
    },
  ] : [];

  return (
    <div className="space-y-4">
      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <AdminDataGrid<LoyaltyTriggerConfigDto>
          rowData={triggers ?? []}
          columnDefs={columnDefs}
          actions={actions}
          emptyMessage="No triggers configured."
          testId="loyalty-triggers-grid"
        />
      )}
      <StepperSheet
        open={form != null}
        onOpenChange={(next) => !next && setForm(null)}
        title={form ? `Edit trigger — ${form.trigger}` : ''}
        steps={steps}
        canSave={!!form && jsonValid && form.expiryDays.trim() !== ''}
        saving={update.isPending}
        onSave={save}
      />
    </div>
  );
}
