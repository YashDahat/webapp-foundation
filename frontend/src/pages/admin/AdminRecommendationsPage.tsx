import { useState } from 'react';
import { Pencil, Trash2 } from 'lucide-react';
import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { type RowAction } from '@/components/admin/RowActionsCell';
import { BulkUploadDialog } from '@/components/admin/BulkUploadDialog';
import { recommendationService } from '@/services/recommendationService';
import {
  useAdminRecommendations, useCreateRecommendation, useDeleteRecommendation, useUpdateRecommendation,
} from '@/hooks/recommendationHooks';
import { useAdminProducts } from '@/hooks/productHooks';
import type { RecommendationDto, RecommendationType, UpsertRecommendationRequest } from '@/types/recommendation';
import { StepperSheet, type WizardStep } from '@/components/admin/StepperSheet';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { ConfirmDialog } from '@/components/ConfirmDialog';

const TYPES: RecommendationType[] = ['UPSELL', 'CROSS_SELL'];
// Customer-facing labels for each relation type (UPSELL surfaces as "Similar products" on the storefront).
const TYPE_LABELS: Record<RecommendationType, string> = {
  UPSELL: 'Similar products',
  CROSS_SELL: 'Frequently bought together',
};
// Only PRODUCT ships a resolver in the default catalog; a business adds more as it adds resolvers.
const ITEM_TYPES = ['PRODUCT'];

interface RecForm {
  id: number | null;
  sourceItemType: string;
  sourceItemId: number | null;
  targetItemType: string;
  targetItemId: number | null;
  relationType: RecommendationType;
  score: string;
  active: boolean;
}

const emptyForm = (): RecForm => ({
  id: null, sourceItemType: 'PRODUCT', sourceItemId: null, targetItemType: 'PRODUCT', targetItemId: null,
  relationType: 'UPSELL', score: '0', active: true,
});

const toForm = (r: RecommendationDto): RecForm => ({
  id: r.id,
  sourceItemType: r.sourceItemType,
  sourceItemId: r.sourceItemId,
  targetItemType: r.targetItemType,
  targetItemId: r.targetItemId,
  relationType: r.relationType,
  score: String(r.score ?? 0),
  active: r.active,
});

/** Admin Recommendations: an AdminDataGrid of all recommendations with create/edit (item pickers) + bulk upload. */
export default function AdminRecommendationsPage() {
  const { data: recs, isLoading } = useAdminRecommendations();
  const { data: products } = useAdminProducts();
  const create = useCreateRecommendation();
  const update = useUpdateRecommendation();
  const remove = useDeleteRecommendation();

  const [form, setForm] = useState<RecForm | null>(null);
  const [toDelete, setToDelete] = useState<RecommendationDto | null>(null);
  const patch = (p: Partial<RecForm>) => setForm((f) => (f ? { ...f, ...p } : f));

  const saving = create.isPending || update.isPending;
  const canSave =
    !!form && form.sourceItemId != null && form.targetItemId != null &&
    !(form.sourceItemType === form.targetItemType && form.sourceItemId === form.targetItemId);

  const save = () => {
    if (!form) return;
    const req: UpsertRecommendationRequest = {
      sourceItemType: form.sourceItemType,
      sourceItemId: form.sourceItemId as number,
      targetItemType: form.targetItemType,
      targetItemId: form.targetItemId as number,
      relationType: form.relationType,
      score: form.score.trim() === '' ? 0 : Number(form.score),
      active: form.active,
    };
    const after = () => setForm(null);
    if (form.id == null) {
      create.mutate(req, { onSuccess: after });
    } else {
      update.mutate({ id: form.id, req }, { onSuccess: after });
    }
  };

  const columnDefs: ColDef<RecommendationDto>[] = [
    { field: 'id', headerName: '#', maxWidth: 90, flex: 0 },
    {
      headerName: 'Source',
      valueGetter: (p) => p.data?.sourceItemName ?? `${p.data?.sourceItemType}:${p.data?.sourceItemId}`,
      flex: 1.3,
    },
    {
      headerName: 'Target',
      valueGetter: (p) => p.data?.targetItemName ?? `${p.data?.targetItemType}:${p.data?.targetItemId}`,
      flex: 1.3,
    },
    { field: 'relationType', headerName: 'Type', valueFormatter: (p) => TYPE_LABELS[p.value as RecommendationType] ?? p.value },
    { field: 'score', maxWidth: 110 },
    { field: 'active', valueFormatter: (p) => (p.value ? 'Yes' : 'No'), maxWidth: 100 },
  ];

  const actions: RowAction<RecommendationDto>[] = [
    { label: 'Edit', icon: Pencil, onClick: (row) => setForm(toForm(row)) },
    {
      label: 'Delete',
      icon: Trash2,
      danger: true,
      onClick: (row) => setToDelete(row),
    },
  ];

  // Product picker for a PRODUCT-typed item; other types fall back to a numeric id input.
  const itemPicker = (itemType: string, value: number | null, onChange: (id: number | null) => void) =>
    itemType === 'PRODUCT' ? (
      <Select value={value != null ? String(value) : ''} onValueChange={(v) => onChange(Number(v))}>
        <SelectTrigger><SelectValue placeholder="Select a product" /></SelectTrigger>
        <SelectContent>
          {(products ?? []).map((p) => (
            <SelectItem key={p.id} value={String(p.id)}>{p.name}</SelectItem>
          ))}
        </SelectContent>
      </Select>
    ) : (
      <Input
        type="number"
        placeholder="Item id"
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))}
      />
    );

  const recSteps: WizardStep[] = form ? [
    {
      id: 'items',
      title: 'Source & target',
      description: 'What recommends what',
      error: form.sourceItemId == null || form.targetItemId == null,
      complete: form.sourceItemId != null && form.targetItemId != null,
      content: (
        <>
          {/* Source */}
          <div className="grid gap-2">
            <Label>Source item</Label>
            <div className="grid grid-cols-[120px_1fr] gap-2">
              <Select value={form.sourceItemType} onValueChange={(v) => patch({ sourceItemType: v, sourceItemId: null })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {ITEM_TYPES.map((t) => <SelectItem key={t} value={t}>{t}</SelectItem>)}
                </SelectContent>
              </Select>
              {itemPicker(form.sourceItemType, form.sourceItemId, (id) => patch({ sourceItemId: id }))}
            </div>
          </div>

          {/* Target */}
          <div className="grid gap-2">
            <Label>Target item</Label>
            <div className="grid grid-cols-[120px_1fr] gap-2">
              <Select value={form.targetItemType} onValueChange={(v) => patch({ targetItemType: v, targetItemId: null })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {ITEM_TYPES.map((t) => <SelectItem key={t} value={t}>{t}</SelectItem>)}
                </SelectContent>
              </Select>
              {itemPicker(form.targetItemType, form.targetItemId, (id) => patch({ targetItemId: id }))}
            </div>
          </div>

          {form.sourceItemType === form.targetItemType && form.sourceItemId != null &&
            form.sourceItemId === form.targetItemId && (
              <p className="text-sm text-destructive">An item cannot recommend itself.</p>
            )}
        </>
      ),
    },
    {
      id: 'settings',
      title: 'Settings',
      description: 'Type, score, active',
      content: (
        <>
          <div className="grid grid-cols-2 gap-4">
            <div className="grid gap-2">
              <Label>Type</Label>
              <Select value={form.relationType} onValueChange={(v) => patch({ relationType: v as RecommendationType })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {TYPES.map((t) => <SelectItem key={t} value={t}>{TYPE_LABELS[t]}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="r-score">Score</Label>
              <Input id="r-score" type="number" value={form.score} onChange={(e) => patch({ score: e.target.value })} />
            </div>
          </div>

          <div className="flex items-center justify-between">
            <Label htmlFor="r-active">Active</Label>
            <Switch id="r-active" checked={form.active} onCheckedChange={(v) => patch({ active: v })} />
          </div>
        </>
      ),
    },
  ] : [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Recommendations</h1>
        <div className="flex gap-2">
          <BulkUploadDialog
            title="Bulk upload recommendations"
            columns={['sourceItemType', 'sourceItemId', 'targetItemType', 'targetItemId', 'relationType', 'score', 'active']}
            sampleRow={['PRODUCT', '1', 'PRODUCT', '2', 'UPSELL', '10', 'true']}
            templateName="recommendations-template.csv"
            onUpload={(file) => recommendationService.bulkUpload(file)}
          />
          <Button onClick={() => setForm(emptyForm())}>New recommendation</Button>
        </div>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <AdminDataGrid<RecommendationDto>
          rowData={recs ?? []}
          columnDefs={columnDefs}
          actions={actions}
          emptyMessage="No recommendations."
          testId="admin-recommendations-grid"
        />
      )}

      <StepperSheet
        open={form != null}
        onOpenChange={(next) => !next && setForm(null)}
        title={form?.id == null ? 'New recommendation' : 'Edit recommendation'}
        steps={recSteps}
        canSave={canSave}
        saving={saving}
        onSave={save}
      />

      <ConfirmDialog
        open={toDelete != null}
        onOpenChange={() => setToDelete(null)}
        title="Delete recommendation?"
        description="This will permanently delete this recommendation. This action cannot be undone."
        confirmLabel="Delete"
        pendingLabel="Deleting…"
        pending={remove.isPending}
        destructive
        onConfirm={() => toDelete && remove.mutate(toDelete.id, { onSuccess: () => setToDelete(null) })}
      />
    </div>
  );
}
