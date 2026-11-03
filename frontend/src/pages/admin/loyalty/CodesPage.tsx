import { useState } from 'react';
import { Ban, Pencil, Trash2 } from 'lucide-react';
import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { type RowAction } from '@/components/admin/RowActionsCell';
import { BulkUploadDialog } from '@/components/admin/BulkUploadDialog';
import { UserCombobox } from '@/components/admin/UserCombobox';
import { StepperSheet, type WizardStep } from '@/components/admin/StepperSheet';
import { loyaltyService } from '@/services/loyaltyService';
import {
  useCreateOffer, useDeleteOffer, useLoyaltyOffers, useRevokeOffer, useUpdateOffer,
} from '@/hooks/loyaltyHooks';
import type { CreateOfferRequest, OfferDto, OfferTrigger, OfferType, UpdateOfferRequest } from '@/types/loyalty';
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
  trigger: OfferTrigger;
  type: OfferType;
  value: string;
  minSpend: string;
  maxDiscount: string;
  maxUses: string;
  campaignId: string;
  startsAt: string;
  expiresAt: string;
}

// The categories an admin may tag a hand-created code with (a reporting label; the backend rejects the
// engine-only triggers like WELCOME/WIN_BACK). Must stay in sync with ADMIN_TRIGGERS in OfferAdminService.
const TRIGGER_OPTIONS: [OfferTrigger, string][] = [
  ['FESTIVE', 'Festive campaign'],
  ['MANUAL', 'Manual / personal'],
  ['MILESTONE', 'Milestone'],
  ['TIER_UP', 'Tier-up'],
  ['SURPRISE', 'Surprise'],
];

const emptyForm = (): OfferForm => ({
  code: '', userId: '', trigger: 'FESTIVE', type: 'PCT', value: '', minSpend: '', maxDiscount: '', maxUses: '', campaignId: '', startsAt: '', expiresAt: '',
});

const numOrNull = (s: string): number | null => (s.trim() === '' ? null : Number(s));
const isoOrNull = (s: string): string | null => (s.trim() ? new Date(s).toISOString() : null);

/** ISO (UTC) → the `YYYY-MM-DDThh:mm` a datetime-local input expects, in the admin's local time. */
function isoToLocalInput(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function toRequest(f: OfferForm): CreateOfferRequest {
  return {
    code: f.code.trim() || null,
    userId: numOrNull(f.userId),
    trigger: f.trigger,
    type: f.type,
    value: numOrNull(f.value),
    minSpend: numOrNull(f.minSpend),
    maxDiscount: numOrNull(f.maxDiscount),
    maxUses: numOrNull(f.maxUses),
    campaignId: f.campaignId.trim() || null,
    startsAt: isoOrNull(f.startsAt),
    expiresAt: isoOrNull(f.expiresAt),
  };
}

/** Edit payload — code and userId are immutable, so they're dropped. */
function toUpdateRequest(f: OfferForm): UpdateOfferRequest {
  return {
    trigger: f.trigger,
    type: f.type,
    value: numOrNull(f.value),
    minSpend: numOrNull(f.minSpend),
    maxDiscount: numOrNull(f.maxDiscount),
    maxUses: numOrNull(f.maxUses),
    campaignId: f.campaignId.trim() || null,
    startsAt: isoOrNull(f.startsAt),
    expiresAt: isoOrNull(f.expiresAt),
  };
}

/** Client-side field validation; returns a map of field → message (empty = valid). */
function validate(f: OfferForm): Partial<Record<keyof OfferForm, string>> {
  const e: Partial<Record<keyof OfferForm, string>> = {};
  const needsValue = f.type === 'PCT' || f.type === 'FLAT' || f.type === 'COINS';
  if (needsValue) {
    const v = Number(f.value);
    if (f.value.trim() === '' || Number.isNaN(v) || v <= 0) e.value = 'Enter a value greater than 0.';
    else if (f.type === 'PCT' && v > 100) e.value = 'Percentage can’t exceed 100.';
  }
  if (f.minSpend.trim() !== '' && Number(f.minSpend) < 0) e.minSpend = 'Must be 0 or more.';
  if (f.maxDiscount.trim() !== '' && Number(f.maxDiscount) < 0) e.maxDiscount = 'Must be 0 or more.';
  if (f.maxUses.trim() !== '' && (!Number.isInteger(Number(f.maxUses)) || Number(f.maxUses) < 1)) {
    e.maxUses = 'Must be a whole number of 1 or more.';
  }
  if (f.startsAt.trim() && f.expiresAt.trim() && new Date(f.startsAt).getTime() >= new Date(f.expiresAt).getTime()) {
    e.expiresAt = 'Expires must be after Starts.';
  }
  return e;
}

function formFromOffer(o: OfferDto): OfferForm {
  return {
    code: o.code,
    userId: o.userId == null ? '' : String(o.userId),
    trigger: o.trigger,
    type: o.type,
    value: o.value == null ? '' : String(o.value),
    minSpend: o.minSpend == null ? '' : String(o.minSpend),
    maxDiscount: o.maxDiscount == null ? '' : String(o.maxDiscount),
    maxUses: o.maxUses == null ? '' : String(o.maxUses),
    campaignId: o.campaignId ?? '',
    startsAt: isoToLocalInput(o.startsAt),
    expiresAt: isoToLocalInput(o.expiresAt),
  };
}

/** Discount codes: engine-issued personal codes + hand-created manual/public campaign codes. Bulk import for campaigns. */
export default function LoyaltyCodesPage() {
  const { data: offers, isLoading } = useLoyaltyOffers();
  const create = useCreateOffer();
  const update = useUpdateOffer();
  const revoke = useRevokeOffer();
  const del = useDeleteOffer();
  const [form, setForm] = useState<OfferForm | null>(null);
  // Id of the code being edited; null while creating a new one.
  const [editingId, setEditingId] = useState<number | null>(null);
  const [toRevoke, setToRevoke] = useState<OfferDto | null>(null);
  const [toDelete, setToDelete] = useState<OfferDto | null>(null);
  const patch = (p: Partial<OfferForm>) => setForm((f) => (f ? { ...f, ...p } : f));

  const editing = editingId != null;
  const errors = form ? validate(form) : {};
  const isValid = Object.keys(errors).length === 0;
  // Red-border highlight for an invalid field (messages themselves live in the top error bar).
  const errClass = (field: keyof OfferForm) => (errors[field] ? 'border-destructive focus-visible:ring-destructive' : undefined);
  const closeForm = () => { setForm(null); setEditingId(null); };
  const openCreate = () => { setEditingId(null); setForm(emptyForm()); };
  const openEdit = (o: OfferDto) => { setEditingId(o.id); setForm(formFromOffer(o)); };

  const save = () => {
    if (!form || !isValid) return;
    if (editingId != null) {
      update.mutate({ id: editingId, req: toUpdateRequest(form) }, { onSuccess: closeForm });
    } else {
      create.mutate(toRequest(form), { onSuccess: closeForm });
    }
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
    { label: 'Edit', icon: Pencil, onClick: (row) => openEdit(row) },
    { label: 'Revoke', icon: Ban, danger: true, onClick: (row) => setToRevoke(row) },
    // Hard delete only for never-redeemed codes; used ones must be revoked (keeps the history).
    { label: 'Delete', icon: Trash2, danger: true, hidden: (row) => (row.usedCount ?? 0) > 0, onClick: (row) => setToDelete(row) },
  ];

  // In edit mode, keep the code's current category selectable even if it's an engine-issued label.
  const triggerOptions: [OfferTrigger, string][] = form && !TRIGGER_OPTIONS.some(([v]) => v === form.trigger)
    ? [[form.trigger, `${form.trigger} (engine)`], ...TRIGGER_OPTIONS]
    : TRIGGER_OPTIONS;

  const steps: WizardStep[] = form ? [
    {
      id: 'code',
      title: editing ? 'Edit code' : 'New code',
      description: editing ? 'Code and customer are fixed' : 'Manual or public campaign',
      content: (
        <>
          {!isValid && (
            <div role="alert" className="rounded-md border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive">
              <p className="font-medium">Please fix the following:</p>
              <ul className="mt-1 list-disc space-y-0.5 pl-5">
                {Object.values(errors).map((m) => <li key={m}>{m}</li>)}
              </ul>
            </div>
          )}
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-2">
              <Label htmlFor="o-code">Code{editing ? '' : ' (blank = auto)'}</Label>
              <Input id="o-code" value={form.code} disabled={editing} onChange={(e) => patch({ code: e.target.value })} />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="o-user">Customer (blank = public)</Label>
              {editing ? (
                <Input
                  id="o-user"
                  disabled
                  value={form.userId === ''
                    ? 'Public offer (no user)'
                    : (offers?.find((o) => o.id === editingId)?.userEmail ?? `User #${form.userId}`)}
                />
              ) : (
                <UserCombobox
                  id="o-user"
                  value={form.userId === '' ? null : Number(form.userId)}
                  onChange={(userId) => patch({ userId: userId == null ? '' : String(userId) })}
                />
              )}
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
              <Label>Category</Label>
              <Select value={form.trigger} onValueChange={(v) => patch({ trigger: v as OfferTrigger })}>
                <SelectTrigger data-testid="o-trigger"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {triggerOptions.map(([value, label]) => (
                    <SelectItem key={value} value={value}>{label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="o-value">Value (% / ₹ / coins)</Label>
            <Input id="o-value" type="number" className={errClass('value')} value={form.value} onChange={(e) => patch({ value: e.target.value })} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-2">
              <Label htmlFor="o-min">Min spend ₹</Label>
              <Input id="o-min" type="number" className={errClass('minSpend')} value={form.minSpend} onChange={(e) => patch({ minSpend: e.target.value })} />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="o-max">Max discount ₹</Label>
              <Input id="o-max" type="number" className={errClass('maxDiscount')} value={form.maxDiscount} onChange={(e) => patch({ maxDiscount: e.target.value })} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-2"><Label htmlFor="o-starts">Starts (blank = now)</Label><Input id="o-starts" type="datetime-local" className={errClass('startsAt')} max={form.expiresAt || undefined} value={form.startsAt} onChange={(e) => patch({ startsAt: e.target.value })} /></div>
            <div className="grid gap-2">
              <Label htmlFor="o-exp">Expires (blank = never)</Label>
              <Input id="o-exp" type="datetime-local" className={errClass('expiresAt')} min={form.startsAt || undefined} value={form.expiresAt} onChange={(e) => patch({ expiresAt: e.target.value })} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-2">
              <Label htmlFor="o-uses">Max uses (public)</Label>
              <Input id="o-uses" type="number" className={errClass('maxUses')} value={form.maxUses} onChange={(e) => patch({ maxUses: e.target.value })} />
            </div>
            <div className="grid gap-2"><Label htmlFor="o-campaign">Campaign id (public)</Label><Input id="o-campaign" value={form.campaignId} onChange={(e) => patch({ campaignId: e.target.value })} /></div>
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
        <Button onClick={openCreate}>New code</Button>
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
        onOpenChange={(next) => !next && closeForm()}
        title={editing ? 'Edit code' : 'New code'}
        steps={steps}
        canSave={!!form && isValid}
        saving={create.isPending || update.isPending}
        saveLabel={editing ? 'Save changes' : 'Save'}
        onSave={save}
        widthClassName="sm:max-w-3xl"
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

      <ConfirmDialog
        open={toDelete != null}
        onOpenChange={() => setToDelete(null)}
        title="Delete code?"
        description={<>This permanently deletes “{toDelete?.code}”. This can't be undone — to keep a record instead, revoke it.</>}
        confirmLabel="Delete"
        pendingLabel="Deleting…"
        pending={del.isPending}
        destructive
        onConfirm={() => toDelete && del.mutate(toDelete.id, { onSuccess: () => setToDelete(null) })}
      />
    </div>
  );
}
