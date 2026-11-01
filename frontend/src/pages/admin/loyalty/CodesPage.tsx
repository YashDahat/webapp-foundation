import { useState } from 'react';
import { Ban } from 'lucide-react';
import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { type RowAction } from '@/components/admin/RowActionsCell';
import { BulkUploadDialog } from '@/components/admin/BulkUploadDialog';
import { StepperSheet, type WizardStep } from '@/components/admin/StepperSheet';
import { loyaltyService } from '@/services/loyaltyService';
import { useCreateOffer, useLoyaltyOffers, useRevokeOffer } from '@/hooks/loyaltyHooks';
import type { CreateOfferRequest, OfferDto, OfferType } from '@/types/loyalty';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';

interface OfferForm {
  code: string;
  userId: string;
  type: OfferType;
  value: string;
  minSpend: string;
  maxDiscount: string;
  maxUses: string;
  campaignId: string;
  expiresAt: string;
}

const emptyForm = (): OfferForm => ({
  code: '', userId: '', type: 'PCT', value: '', minSpend: '', maxDiscount: '', maxUses: '', campaignId: '', expiresAt: '',
});

const numOrNull = (s: string): number | null => (s.trim() === '' ? null : Number(s));

function toRequest(f: OfferForm): CreateOfferRequest {
  return {
    code: f.code.trim() || null,
    userId: numOrNull(f.userId),
    type: f.type,
    value: numOrNull(f.value),
    minSpend: numOrNull(f.minSpend),
    maxDiscount: numOrNull(f.maxDiscount),
    maxUses: numOrNull(f.maxUses),
    campaignId: f.campaignId.trim() || null,
    expiresAt: f.expiresAt.trim() ? new Date(f.expiresAt).toISOString() : null,
  };
}

/** Discount codes: engine-issued personal codes + hand-created manual/public campaign codes. Bulk import for campaigns. */
export default function LoyaltyCodesPage() {
  const { data: offers, isLoading } = useLoyaltyOffers();
  const create = useCreateOffer();
  const revoke = useRevokeOffer();
  const [form, setForm] = useState<OfferForm | null>(null);
  const [toRevoke, setToRevoke] = useState<OfferDto | null>(null);
  const patch = (p: Partial<OfferForm>) => setForm((f) => (f ? { ...f, ...p } : f));

  const save = () => {
    if (!form) return;
    create.mutate(toRequest(form), { onSuccess: () => setForm(null) });
  };

  const columnDefs: ColDef<OfferDto>[] = [
    { field: 'code', flex: 1.2 },
    { field: 'trigger', maxWidth: 150 },
    { field: 'type', maxWidth: 120 },
    { field: 'value', maxWidth: 110, valueFormatter: (p) => (p.value == null ? '—' : String(p.value)) },
    { headerName: 'Scope', valueGetter: (p) => (p.data?.userId == null ? 'Public' : (p.data?.userEmail ?? `User #${p.data?.userId}`)), flex: 1 },
    { headerName: 'Used', valueGetter: (p) => `${p.data?.usedCount ?? 0}/${p.data?.maxUses ?? 0}`, maxWidth: 110 },
    { field: 'status', maxWidth: 120 },
    { headerName: 'Expires', valueGetter: (p) => (p.data?.expiresAt ? new Date(p.data.expiresAt).toLocaleDateString() : '—'), maxWidth: 140 },
  ];

  const actions: RowAction<OfferDto>[] = [
    { label: 'Revoke', icon: Ban, danger: true, onClick: (row) => setToRevoke(row) },
  ];

  const steps: WizardStep[] = form ? [
    {
      id: 'code',
      title: 'New code',
      description: 'Manual or public campaign',
      content: (
        <>
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-2">
              <Label htmlFor="o-code">Code (blank = auto)</Label>
              <Input id="o-code" value={form.code} onChange={(e) => patch({ code: e.target.value })} />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="o-user">User id (blank = public)</Label>
              <Input id="o-user" type="number" value={form.userId} onChange={(e) => patch({ userId: e.target.value })} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-2">
              <Label>Type</Label>
              <Select value={form.type} onValueChange={(v) => patch({ type: v as OfferType })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="PCT">Percentage off</SelectItem>
                  <SelectItem value="FLAT">Flat ₹ off</SelectItem>
                  <SelectItem value="FREE_SHIP">Free shipping</SelectItem>
                  <SelectItem value="GIFT">Gift</SelectItem>
                  <SelectItem value="COINS">Bonus coins</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="o-value">Value (% / ₹ / coins)</Label>
              <Input id="o-value" type="number" value={form.value} onChange={(e) => patch({ value: e.target.value })} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-2"><Label htmlFor="o-min">Min spend ₹</Label><Input id="o-min" type="number" value={form.minSpend} onChange={(e) => patch({ minSpend: e.target.value })} /></div>
            <div className="grid gap-2"><Label htmlFor="o-max">Max discount ₹</Label><Input id="o-max" type="number" value={form.maxDiscount} onChange={(e) => patch({ maxDiscount: e.target.value })} /></div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-2"><Label htmlFor="o-uses">Max uses (public)</Label><Input id="o-uses" type="number" value={form.maxUses} onChange={(e) => patch({ maxUses: e.target.value })} /></div>
            <div className="grid gap-2"><Label htmlFor="o-exp">Expires</Label><Input id="o-exp" type="date" value={form.expiresAt} onChange={(e) => patch({ expiresAt: e.target.value })} /></div>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="o-campaign">Campaign id (public)</Label>
            <Input id="o-campaign" value={form.campaignId} onChange={(e) => patch({ campaignId: e.target.value })} />
          </div>
        </>
      ),
    },
  ] : [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-end gap-2">
        <BulkUploadDialog
          title="Bulk upload campaign codes"
          columns={['code', 'type', 'value', 'minSpend', 'maxDiscount', 'maxUses', 'campaignId', 'expiresAt']}
          sampleRow={['DIWALI10', 'PCT', '10', '999', '300', '500', 'diwali-2026', '2026-11-15T00:00:00Z']}
          templateName="loyalty-codes-template.csv"
          onUpload={(file) => loyaltyService.bulkUploadOffers(file)}
        />
        <Button onClick={() => setForm(emptyForm())}>New code</Button>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <AdminDataGrid<OfferDto>
          rowData={offers ?? []}
          columnDefs={columnDefs}
          actions={actions}
          emptyMessage="No codes yet."
          testId="loyalty-codes-grid"
        />
      )}

      <StepperSheet
        open={form != null}
        onOpenChange={(next) => !next && setForm(null)}
        title="New code"
        steps={steps}
        canSave={!!form}
        saving={create.isPending}
        onSave={save}
      />

      <ConfirmDialog
        open={toRevoke != null}
        onOpenChange={() => setToRevoke(null)}
        title="Revoke code?"
        description={<>This will revoke “{toRevoke?.code}” so it can no longer be redeemed.</>}
        confirmLabel="Revoke"
        pendingLabel="Revoking…"
        pending={revoke.isPending}
        destructive
        onConfirm={() => toRevoke && revoke.mutate(toRevoke.id, { onSuccess: () => setToRevoke(null) })}
      />
    </div>
  );
}
