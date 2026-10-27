import { useState } from 'react';
import { Pencil, Trash2 } from 'lucide-react';
import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { type RowAction } from '@/components/admin/RowActionsCell';
import { BulkUploadDialog } from '@/components/admin/BulkUploadDialog';
import ImagePicker from '@/components/admin/ImagePicker';
import MultiImagePicker from '@/components/admin/MultiImagePicker';
import { eventService } from '@/services/eventService';
import { useAdminEvents, useCreateEvent, useDeleteEvent, useUpdateEvent } from '@/hooks/eventHooks';
import { useAdminBranches } from '@/hooks/branchHooks';
import type { EventDto, UpsertEventRequest } from '@/types/event';
import { StepperSheet, type WizardStep } from '@/components/admin/StepperSheet';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Switch } from '@/components/ui/switch';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { formatEventDate } from '@/pages/EventsPage';

const NO_VENUE = '__none__';

interface TierForm {
  id: number | null;
  label: string;
  sku: string;
  price: string;
  stock: string;
}

interface EventForm {
  id: number | null;
  name: string;
  price: string;
  category: string;
  description: string;
  active: boolean;
  startsAt: string;      // datetime-local value
  endsAt: string;
  salesCloseAt: string;
  venueBranchId: string; // branch id or NO_VENUE
  capacity: string;
  imageUrl: string;
  images: string[];
  features: string[];
  tiers: TierForm[];
}

// ── ISO ⇄ datetime-local helpers ──
const pad = (n: number) => String(n).padStart(2, '0');
function toLocalInput(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
function fromLocalInput(local: string): string | null {
  if (!local.trim()) return null;
  const d = new Date(local);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

const emptyForm = (): EventForm => ({
  id: null, name: '', price: '', category: '', description: '', active: true,
  startsAt: '', endsAt: '', salesCloseAt: '', venueBranchId: NO_VENUE, capacity: '',
  imageUrl: '', images: [], features: [], tiers: [],
});

const toForm = (e: EventDto): EventForm => ({
  id: e.id,
  name: e.name,
  price: String(e.price),
  category: e.category ?? '',
  description: e.description ?? '',
  active: e.active,
  startsAt: toLocalInput(e.startsAt),
  endsAt: toLocalInput(e.endsAt),
  salesCloseAt: toLocalInput(e.salesCloseAt),
  venueBranchId: e.venueBranchId != null ? String(e.venueBranchId) : NO_VENUE,
  capacity: e.capacity != null ? String(e.capacity) : '',
  imageUrl: e.imageUrl ?? '',
  images: e.images ?? [],
  features: e.features ?? [],
  tiers: e.tiers.map((t) => ({
    id: t.id,
    label: t.label,
    sku: t.sku ?? '',
    price: t.price != null ? String(t.price) : '',
    stock: t.stock != null ? String(t.stock) : '',
  })),
});

const num = (s: string): number | null => (s.trim() === '' ? null : Number(s));

function toRequest(f: EventForm): UpsertEventRequest {
  return {
    name: f.name.trim(),
    price: Number(f.price),
    category: f.category.trim() || null,
    description: f.description.trim() || null,
    active: f.active,
    startsAt: fromLocalInput(f.startsAt) ?? new Date().toISOString(),
    endsAt: fromLocalInput(f.endsAt),
    salesCloseAt: fromLocalInput(f.salesCloseAt),
    venueBranchId: f.venueBranchId === NO_VENUE ? null : Number(f.venueBranchId),
    capacity: num(f.capacity),
    imageUrl: f.imageUrl.trim() || null,
    images: f.images,
    features: f.features.map((x) => x.trim()).filter((x) => x !== ''),
    tiers: f.tiers
      .filter((t) => t.label.trim() !== '')
      .map((t) => ({
        id: t.id,
        label: t.label.trim(),
        sku: t.sku.trim() || null,
        price: num(t.price),
        stock: num(t.stock),
      })),
  };
}

/** Admin Events: an AdminDataGrid of all events with create/edit (schedule, venue, capacity, tiers) + bulk upload. */
export default function AdminEventsPage() {
  const { data: events, isLoading } = useAdminEvents();
  const { data: branches } = useAdminBranches();
  const create = useCreateEvent();
  const update = useUpdateEvent();
  const remove = useDeleteEvent();

  const [form, setForm] = useState<EventForm | null>(null);
  const patch = (p: Partial<EventForm>) => setForm((f) => (f ? { ...f, ...p } : f));
  const [toDelete, setToDelete] = useState<EventDto | null>(null);

  const saving = create.isPending || update.isPending;
  const priceValid = !!form && form.price.trim() !== '' && Number.isFinite(Number(form.price));
  const canSave = !!form && form.name.trim() !== '' && priceValid && form.startsAt.trim() !== '';

  const save = () => {
    if (!form) return;
    const req = toRequest(form);
    const after = () => setForm(null);
    if (form.id == null) create.mutate(req, { onSuccess: after });
    else update.mutate({ id: form.id, req }, { onSuccess: after });
  };

  const columnDefs: ColDef<EventDto>[] = [
    { field: 'id', headerName: '#', maxWidth: 90, flex: 0 },
    { field: 'name', flex: 1.4 },
    { headerName: 'Date', valueGetter: (p) => (p.data ? formatEventDate(p.data.startsAt) : ''), flex: 1.2 },
    { headerName: 'Venue', valueGetter: (p) => p.data?.venueName ?? '—' },
    { field: 'price', valueFormatter: (p) => (p.value === 0 ? 'Free' : `₹${p.value}`), maxWidth: 120 },
    {
      headerName: 'Load',
      valueGetter: (p) => {
        const d = p.data;
        if (!d) return '';
        if (d.tiers.length > 0) return `${d.tiers.length} tier(s)`;
        if (d.capacity == null) return 'Unlimited';
        return `${(d.capacity ?? 0) - (d.reserved ?? 0)}/${d.capacity} left`;
      },
    },
    { field: 'active', valueFormatter: (p) => (p.value ? 'Yes' : 'No'), maxWidth: 100 },
  ];

  const actions: RowAction<EventDto>[] = [
    { label: 'Edit', icon: Pencil, onClick: (row) => setForm(toForm(row)) },
    { label: 'Delete', icon: Trash2, danger: true, onClick: (row) => setToDelete(row) },
  ];

  const steps: WizardStep[] = form ? [
    {
      id: 'details',
      title: 'Details',
      description: 'Name, price, category',
      error: form.name.trim() === '' || !priceValid,
      complete: form.name.trim() !== '' && priceValid,
      content: (
        <>
          <div className="grid gap-2">
            <Label htmlFor="e-name">Name</Label>
            <Input id="e-name" value={form.name} onChange={(e) => patch({ name: e.target.value })} />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="grid gap-2">
              <Label htmlFor="e-price">Price (₹) — 0 for free</Label>
              <Input id="e-price" type="number" value={form.price} onChange={(e) => patch({ price: e.target.value })} />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="e-category">Category</Label>
              <Input id="e-category" value={form.category} onChange={(e) => patch({ category: e.target.value })} />
            </div>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="e-desc">Description</Label>
            <Textarea id="e-desc" value={form.description} onChange={(e) => patch({ description: e.target.value })} />
          </div>
          <div className="flex items-center justify-between">
            <Label htmlFor="e-active">Active</Label>
            <Switch id="e-active" checked={form.active} onCheckedChange={(v) => patch({ active: v })} />
          </div>
        </>
      ),
    },
    {
      id: 'schedule',
      title: 'Schedule',
      description: 'When & where',
      error: form.startsAt.trim() === '',
      complete: form.startsAt.trim() !== '',
      content: (
        <>
          <div className="grid grid-cols-2 gap-4">
            <div className="grid gap-2">
              <Label htmlFor="e-starts">Starts</Label>
              <Input id="e-starts" type="datetime-local" value={form.startsAt} onChange={(e) => patch({ startsAt: e.target.value })} />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="e-ends">Ends (optional)</Label>
              <Input id="e-ends" type="datetime-local" value={form.endsAt} onChange={(e) => patch({ endsAt: e.target.value })} />
            </div>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="e-closes">Sign-up closes (optional — defaults to start)</Label>
            <Input id="e-closes" type="datetime-local" value={form.salesCloseAt} onChange={(e) => patch({ salesCloseAt: e.target.value })} />
          </div>
          <div className="grid gap-2">
            <Label>Venue</Label>
            <Select value={form.venueBranchId} onValueChange={(v) => patch({ venueBranchId: v })}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value={NO_VENUE}>No specific venue</SelectItem>
                {(branches ?? []).map((b) => (
                  <SelectItem key={b.id} value={String(b.id)}>{b.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </>
      ),
    },
    {
      id: 'media',
      title: 'Media',
      description: 'Thumbnail & gallery',
      content: (
        <>
          <div className="grid gap-2">
            <Label>Thumbnail</Label>
            <ImagePicker value={form.imageUrl} onChange={(url) => patch({ imageUrl: url })} />
          </div>
          <div className="grid gap-2">
            <Label>Gallery</Label>
            <MultiImagePicker value={form.images} onChange={(images) => patch({ images })} />
          </div>
        </>
      ),
    },
    {
      id: 'tickets',
      title: 'Tickets',
      description: 'Capacity & tiers',
      content: (
        <>
          {form.tiers.length === 0 && (
            <div className="grid gap-2 rounded-md border p-3">
              <Label htmlFor="e-capacity">Capacity (seats) — blank = unlimited</Label>
              <Input id="e-capacity" type="number" value={form.capacity} onChange={(e) => patch({ capacity: e.target.value })} />
            </div>
          )}

          <div className="space-y-2 rounded-md border p-3">
            <div className="flex items-center justify-between">
              <Label>Ticket tiers</Label>
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => patch({ tiers: [...form.tiers, { id: null, label: '', sku: '', price: '', stock: '' }] })}
              >
                Add tier
              </Button>
            </div>
            {form.tiers.length === 0 && (
              <p className="text-xs text-muted-foreground">
                No tiers — a single capacity pool above. Add tiers (e.g. Early bird / General / VIP) for per-tier price + seats.
              </p>
            )}
            {form.tiers.map((t, i) => {
              const setT = (p: Partial<TierForm>) =>
                patch({ tiers: form.tiers.map((x, j) => (j === i ? { ...x, ...p } : x)) });
              return (
                <div key={i} className="space-y-2 rounded border p-2">
                  <div className="grid grid-cols-3 gap-2">
                    <Input placeholder="Label (e.g. VIP)" value={t.label} onChange={(e) => setT({ label: e.target.value })} />
                    <Input placeholder="Price" type="number" value={t.price} onChange={(e) => setT({ price: e.target.value })} />
                    <Input placeholder="Seats" type="number" value={t.stock} onChange={(e) => setT({ stock: e.target.value })} />
                  </div>
                  <div className="flex items-center gap-2">
                    <Input placeholder="SKU (optional)" value={t.sku} onChange={(e) => setT({ sku: e.target.value })} />
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      className="ml-auto text-destructive"
                      onClick={() => patch({ tiers: form.tiers.filter((_, j) => j !== i) })}
                    >
                      Remove
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        </>
      ),
    },
  ] : [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Events</h1>
        <div className="flex gap-2">
          <BulkUploadDialog
            title="Bulk upload events"
            columns={['name', 'price', 'category', 'startsAt', 'endsAt', 'capacity', 'venueBranchSlug', 'workflowCode']}
            sampleRow={['Autumn Tasting Night', '0', 'Food', '2026-11-14T18:30:00Z', '2026-11-14T21:30:00Z', '40', '', '']}
            templateName="events-template.csv"
            onUpload={(file) => eventService.bulkUpload(file)}
          />
          <Button onClick={() => setForm(emptyForm())}>New event</Button>
        </div>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <AdminDataGrid<EventDto>
          rowData={events ?? []}
          columnDefs={columnDefs}
          actions={actions}
          emptyMessage="No events."
          testId="admin-events-grid"
        />
      )}

      <StepperSheet
        open={form != null}
        onOpenChange={(next) => !next && setForm(null)}
        title={form?.id == null ? 'New event' : `Edit — ${form?.name}`}
        steps={steps}
        canSave={canSave}
        saving={saving}
        onSave={save}
      />

      <ConfirmDialog
        open={toDelete != null}
        onOpenChange={() => setToDelete(null)}
        title="Delete event?"
        description={<>This will permanently delete “{toDelete?.name}”. This action cannot be undone.</>}
        confirmLabel="Delete"
        pendingLabel="Deleting…"
        pending={remove.isPending}
        destructive
        onConfirm={() => toDelete && remove.mutate(toDelete.id, { onSuccess: () => setToDelete(null) })}
      />
    </div>
  );
}
