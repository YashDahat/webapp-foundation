import { useState } from 'react';
import { Pencil, Trash2 } from 'lucide-react';
import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { type RowAction } from '@/components/admin/RowActionsCell';
import { BulkUploadDialog } from '@/components/admin/BulkUploadDialog';
import { branchService } from '@/services/branchService';
import { useAdminBranches, useCreateBranch, useDeleteBranch, useUpdateBranch } from '@/hooks/branchHooks';
import type { BranchDto, UpsertBranchRequest } from '@/types/branch';
import { StepperSheet, type WizardStep } from '@/components/admin/StepperSheet';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { ConfirmDialog } from '@/components/ConfirmDialog';

interface BranchForm {
  id: number | null;
  name: string;
  phone: string;
  addressLine: string;
  city: string;
  state: string;
  pincode: string;
  latitude: string;
  longitude: string;
  googleMapsUrl: string;
  openingHours: string;
  active: boolean;
}

const emptyForm = (): BranchForm => ({
  id: null, name: '', phone: '', addressLine: '', city: '', state: '', pincode: '',
  latitude: '', longitude: '', googleMapsUrl: '', openingHours: '', active: true,
});

const toForm = (b: BranchDto): BranchForm => ({
  id: b.id, name: b.name, phone: b.phone ?? '', addressLine: b.addressLine ?? '', city: b.city ?? '',
  state: b.state ?? '', pincode: b.pincode ?? '', latitude: b.latitude != null ? String(b.latitude) : '',
  longitude: b.longitude != null ? String(b.longitude) : '', googleMapsUrl: b.googleMapsUrl ?? '',
  openingHours: b.openingHours ?? '', active: b.active,
});

const num = (s: string): number | null => (s.trim() === '' ? null : Number(s));

function toRequest(f: BranchForm): UpsertBranchRequest {
  return {
    name: f.name.trim(),
    phone: f.phone.trim() || null,
    addressLine: f.addressLine.trim() || null,
    city: f.city.trim() || null,
    state: f.state.trim() || null,
    pincode: f.pincode.trim() || null,
    latitude: num(f.latitude),
    longitude: num(f.longitude),
    googleMapsUrl: f.googleMapsUrl.trim() || null,
    openingHours: f.openingHours.trim() || null,
    active: f.active,
  };
}

/** Admin Branches: an AdminDataGrid of outlets with create/edit (lat/lng) + bulk upload. */
export default function AdminBranchesPage() {
  const { data: branches, isLoading } = useAdminBranches();
  const create = useCreateBranch();
  const update = useUpdateBranch();
  const remove = useDeleteBranch();

  const [form, setForm] = useState<BranchForm | null>(null);
  const [toDelete, setToDelete] = useState<BranchDto | null>(null);
  const patch = (p: Partial<BranchForm>) => setForm((f) => (f ? { ...f, ...p } : f));
  const saving = create.isPending || update.isPending;
  const canSave = !!form && form.name.trim() !== '';

  const save = () => {
    if (!form) return;
    const req = toRequest(form);
    const after = () => setForm(null);
    if (form.id == null) create.mutate(req, { onSuccess: after });
    else update.mutate({ id: form.id, req }, { onSuccess: after });
  };

  const columnDefs: ColDef<BranchDto>[] = [
    { field: 'id', headerName: '#', maxWidth: 90, flex: 0 },
    { field: 'name', flex: 1.3 },
    { field: 'city' },
    { field: 'phone' },
    { field: 'active', valueFormatter: (p) => (p.value ? 'Yes' : 'No'), maxWidth: 100 },
  ];

  const actions: RowAction<BranchDto>[] = [
    { label: 'Edit', icon: Pencil, onClick: (row) => setForm(toForm(row)) },
    { label: 'Delete', icon: Trash2, danger: true, onClick: (row) => setToDelete(row) },
  ];

  const branchSteps: WizardStep[] = form ? [
    {
      id: 'details',
      title: 'Details',
      description: 'Name & contact',
      error: form.name.trim() === '',
      complete: form.name.trim() !== '',
      content: (
        <>
          <div className="grid gap-2">
            <Label htmlFor="b-name">Name</Label>
            <Input id="b-name" value={form.name} onChange={(e) => patch({ name: e.target.value })} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-2"><Label htmlFor="b-phone">Phone</Label><Input id="b-phone" value={form.phone} onChange={(e) => patch({ phone: e.target.value })} /></div>
            <div className="grid gap-2"><Label htmlFor="b-hours">Opening hours</Label><Input id="b-hours" value={form.openingHours} onChange={(e) => patch({ openingHours: e.target.value })} /></div>
          </div>
          <div className="flex items-center justify-between">
            <Label htmlFor="b-active">Active</Label>
            <Switch id="b-active" checked={form.active} onCheckedChange={(v) => patch({ active: v })} />
          </div>
        </>
      ),
    },
    {
      id: 'address',
      title: 'Address',
      description: 'Street & city',
      content: (
        <>
          <div className="grid gap-2">
            <Label htmlFor="b-address">Address line</Label>
            <Input id="b-address" value={form.addressLine} onChange={(e) => patch({ addressLine: e.target.value })} />
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div className="grid gap-2"><Label htmlFor="b-city">City</Label><Input id="b-city" value={form.city} onChange={(e) => patch({ city: e.target.value })} /></div>
            <div className="grid gap-2"><Label htmlFor="b-state">State</Label><Input id="b-state" value={form.state} onChange={(e) => patch({ state: e.target.value })} /></div>
            <div className="grid gap-2"><Label htmlFor="b-pin">Pincode</Label><Input id="b-pin" value={form.pincode} onChange={(e) => patch({ pincode: e.target.value })} /></div>
          </div>
        </>
      ),
    },
    {
      id: 'location',
      title: 'Location',
      description: 'Map coordinates',
      content: (
        <>
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-2"><Label htmlFor="b-lat">Latitude</Label><Input id="b-lat" type="number" value={form.latitude} onChange={(e) => patch({ latitude: e.target.value })} /></div>
            <div className="grid gap-2"><Label htmlFor="b-lng">Longitude</Label><Input id="b-lng" type="number" value={form.longitude} onChange={(e) => patch({ longitude: e.target.value })} /></div>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="b-maps">Google Maps link</Label>
            <Input
              id="b-maps"
              placeholder="https://maps.app.goo.gl/… (Share → Copy link)"
              value={form.googleMapsUrl}
              onChange={(e) => patch({ googleMapsUrl: e.target.value })}
            />
            <p className="text-xs text-muted-foreground">
              In Google Maps, open the place → Share → Copy link, and paste it here.
            </p>
          </div>
        </>
      ),
    },
  ] : [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Branches</h1>
        <div className="flex gap-2">
          <BulkUploadDialog
            title="Bulk upload branches"
            columns={['name', 'phone', 'addressLine', 'city', 'state', 'pincode', 'latitude', 'longitude', 'googleMapsUrl', 'openingHours']}
            sampleRow={['Downtown', '+919000000000', '1 MG Road', 'Bengaluru', 'KA', '560001', '12.9716', '77.5946', 'https://maps.app.goo.gl/xxxx', '9am–9pm']}
            templateName="branches-template.csv"
            onUpload={(file) => branchService.bulkUpload(file)}
          />
          <Button onClick={() => setForm(emptyForm())}>New branch</Button>
        </div>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <AdminDataGrid<BranchDto>
          rowData={branches ?? []}
          columnDefs={columnDefs}
          actions={actions}
          emptyMessage="No branches."
          testId="admin-branches-grid"
        />
      )}

      <StepperSheet
        open={form != null}
        onOpenChange={(next) => !next && setForm(null)}
        title={form?.id == null ? 'New branch' : `Edit — ${form?.name}`}
        steps={branchSteps}
        canSave={canSave}
        saving={saving}
        onSave={save}
      />

      <ConfirmDialog
        open={toDelete != null}
        onOpenChange={() => setToDelete(null)}
        title="Delete branch?"
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
