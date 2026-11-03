import { useState } from 'react';
import { Pencil } from 'lucide-react';
import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { type RowAction } from '@/components/admin/RowActionsCell';
import { StepperSheet, type WizardStep } from '@/components/admin/StepperSheet';
import { useLoyaltyTiers, useUpdateLoyaltyTier } from '@/hooks/loyaltyHooks';
import type { LoyaltyTierDto } from '@/types/loyalty';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';

interface TierForm {
  id: number;
  tier: string;
  name: string;
  minScore: string;
  coinsPer100: string;
  perks: string;
}

const toForm = (t: LoyaltyTierDto): TierForm => ({
  id: t.id, tier: t.tier, name: t.name, minScore: String(t.minScore),
  coinsPer100: String(t.coinsPer100), perks: t.perks ?? '',
});

/** The four tiers are fixed; admins tune the score threshold, coins-per-₹100 and perks blurb. */
export default function LoyaltyTiersPage() {
  const { data: tiers, isLoading } = useLoyaltyTiers();
  const update = useUpdateLoyaltyTier();
  const [form, setForm] = useState<TierForm | null>(null);
  const patch = (p: Partial<TierForm>) => setForm((f) => (f ? { ...f, ...p } : f));

  const save = () => {
    if (!form) return;
    update.mutate(
      { id: form.id, req: { name: form.name.trim(), minScore: Number(form.minScore), coinsPer100: Number(form.coinsPer100), perks: form.perks.trim() || null } },
      { onSuccess: () => setForm(null) },
    );
  };

  const columnDefs: ColDef<LoyaltyTierDto>[] = [
    { field: 'tier', maxWidth: 140 },
    { field: 'name', flex: 1 },
    { field: 'minScore', headerName: 'Min score', maxWidth: 140 },
    { field: 'coinsPer100', headerName: 'Coins / ₹100', maxWidth: 160 },
    { field: 'perks', flex: 2 },
  ];

  const actions: RowAction<LoyaltyTierDto>[] = [
    { label: 'Edit', icon: Pencil, onClick: (row) => setForm(toForm(row)) },
  ];

  const steps: WizardStep[] = form ? [
    {
      id: 'tier',
      title: form.tier,
      description: 'Thresholds & perks',
      content: (
        <>
          <div className="grid gap-2">
            <Label htmlFor="t-name">Display name</Label>
            <Input id="t-name" value={form.name} onChange={(e) => patch({ name: e.target.value })} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-2"><Label htmlFor="t-min">Min score</Label><Input id="t-min" type="number" value={form.minScore} onChange={(e) => patch({ minScore: e.target.value })} /></div>
            <div className="grid gap-2"><Label htmlFor="t-coins">Coins per ₹100</Label><Input id="t-coins" type="number" step="0.1" value={form.coinsPer100} onChange={(e) => patch({ coinsPer100: e.target.value })} /></div>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="t-perks">Perks</Label>
            <Textarea id="t-perks" rows={3} value={form.perks} onChange={(e) => patch({ perks: e.target.value })} />
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
        <AdminDataGrid<LoyaltyTierDto>
          rowData={tiers ?? []}
          columnDefs={columnDefs}
          actions={actions}
          emptyMessage="No tiers configured."
          testId="loyalty-tiers-grid"
        />
      )}
      <StepperSheet
        open={form != null}
        onOpenChange={(next) => !next && setForm(null)}
        title={form ? `Edit tier — ${form.tier}` : ''}
        steps={steps}
        canSave={!!form && form.name.trim() !== ''}
        saving={update.isPending}
        onSave={save}
      />
    </div>
  );
}
