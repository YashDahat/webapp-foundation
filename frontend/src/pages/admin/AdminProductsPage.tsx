import { useState } from 'react';
import { Pencil, Trash2 } from 'lucide-react';
import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { type RowAction } from '@/components/admin/RowActionsCell';
import { BulkUploadDialog } from '@/components/admin/BulkUploadDialog';
import ImagePicker from '@/components/admin/ImagePicker';
import MultiImagePicker from '@/components/admin/MultiImagePicker';
import { productService } from '@/services/productService';
import { useAdminProducts, useCreateProduct, useDeleteProduct, useUpdateProduct } from '@/hooks/productHooks';
import { useAdminBranches } from '@/hooks/branchHooks';
import { useWorkflows } from '@/hooks/workflowHooks';
import type { BranchScope, ProductDto, UpsertProductRequest } from '@/types/product';
import { StepperSheet, type WizardStep } from '@/components/admin/StepperSheet';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Switch } from '@/components/ui/switch';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { ConfirmDialog } from '@/components/ConfirmDialog';

// Sentinel Select value for "use the default workflow" (empty workflowCode). shadcn Select items can't be empty strings.
const DEFAULT_WORKFLOW = '__default__';

interface VariantForm {
  id: number | null;
  label: string;
  sku: string;
  price: string;
  stockTracked: boolean;
  stock: string;
}

interface ProductForm {
  id: number | null;
  name: string;
  price: string;
  category: string;
  description: string;
  active: boolean;
  workflowCode: string;
  imageUrl: string;
  images: string[];
  features: string[];
  branchScope: BranchScope;
  availableBranchIds: string; // comma-separated branch ids (branch picker arrives with the branches feature)
  stockTracked: boolean;
  stockPerBranch: boolean;
  stock: string;
  variants: VariantForm[];
}

const emptyForm = (): ProductForm => ({
  id: null, name: '', price: '', category: '', description: '', active: true, workflowCode: '',
  imageUrl: '', images: [], features: [], branchScope: 'GLOBAL', availableBranchIds: '',
  stockTracked: false, stockPerBranch: false, stock: '', variants: [],
});

const toForm = (p: ProductDto): ProductForm => ({
  id: p.id,
  name: p.name,
  price: String(p.price),
  category: p.category ?? '',
  description: p.description ?? '',
  active: p.active,
  workflowCode: p.workflowCode ?? '',
  imageUrl: p.imageUrl ?? '',
  images: p.images ?? [],
  features: p.features ?? [],
  branchScope: p.branchScope,
  availableBranchIds: (p.availableBranchIds ?? []).join(', '),
  stockTracked: p.stockTracked,
  stockPerBranch: p.stockPerBranch,
  stock: p.stock != null ? String(p.stock) : '',
  variants: p.variants.map((v) => ({
    id: v.id,
    label: v.label,
    sku: v.sku ?? '',
    price: v.price != null ? String(v.price) : '',
    stockTracked: v.stockTracked,
    stock: v.stock != null ? String(v.stock) : '',
  })),
});

const num = (s: string): number | null => (s.trim() === '' ? null : Number(s));

const parseIds = (s: string): number[] =>
  s.split(',').map((x) => Number(x.trim())).filter((n) => Number.isFinite(n));

function toRequest(f: ProductForm): UpsertProductRequest {
  const hasVariants = f.variants.some((v) => v.label.trim() !== '');
  return {
    name: f.name.trim(),
    price: Number(f.price),
    category: f.category.trim() || null,
    description: f.description.trim() || null,
    active: f.active,
    workflowCode: f.workflowCode.trim() || null,
    imageUrl: f.imageUrl.trim() || null,
    images: f.images,
    features: f.features.map((x) => x.trim()).filter((x) => x !== ''),
    branchScope: f.branchScope,
    availableBranchIds: f.branchScope === 'SELECTED' ? parseIds(f.availableBranchIds) : [],
    stockTracked: f.stockTracked,
    stockPerBranch: f.stockPerBranch,
    stock: !hasVariants && f.stockTracked && !f.stockPerBranch ? num(f.stock) : null,
    variants: f.variants
      .filter((v) => v.label.trim() !== '')
      .map((v) => ({
        id: v.id,
        label: v.label.trim(),
        sku: v.sku.trim() || null,
        price: num(v.price),
        stockTracked: v.stockTracked,
        stock: v.stockTracked ? num(v.stock) : null,
      })),
  };
}

/** Admin Products: an AdminDataGrid of all products with create/edit (images, branch, stock, variants) + bulk upload. */
export default function AdminProductsPage() {
  const { data: products, isLoading } = useAdminProducts();
  const { data: branches } = useAdminBranches();
  const { data: workflows } = useWorkflows();
  const create = useCreateProduct();
  const update = useUpdateProduct();
  const remove = useDeleteProduct();

  const [form, setForm] = useState<ProductForm | null>(null);
  const patch = (p: Partial<ProductForm>) => setForm((f) => (f ? { ...f, ...p } : f));

  // Product pending delete-confirmation (shadcn AlertDialog instead of window.confirm).
  const [toDelete, setToDelete] = useState<ProductDto | null>(null);

  const saving = create.isPending || update.isPending;
  const canSave = !!form && form.name.trim() !== '' && form.price.trim() !== '' && Number.isFinite(Number(form.price));

  const save = () => {
    if (!form) return;
    const req = toRequest(form);
    const after = () => setForm(null);
    if (form.id == null) {
      create.mutate(req, { onSuccess: after });
    } else {
      update.mutate({ id: form.id, req }, { onSuccess: after });
    }
  };

  const columnDefs: ColDef<ProductDto>[] = [
    { field: 'id', headerName: '#', maxWidth: 90, flex: 0 },
    { field: 'name', flex: 1.4 },
    { field: 'category' },
    { field: 'price', valueFormatter: (p) => `₹${p.value}`, maxWidth: 120 },
    {
      headerName: 'Stock',
      valueGetter: (p) => {
        const d = p.data;
        if (!d) return '';
        if (d.variants.length > 0) return `${d.variants.length} variant(s)`;
        if (!d.stockTracked) return 'Unlimited';
        if (d.stockPerBranch) return 'Per-branch';
        return d.available != null ? `${d.available} left` : '';
      },
    },
    { field: 'active', valueFormatter: (p) => (p.value ? 'Yes' : 'No'), maxWidth: 100 },
  ];

  const actions: RowAction<ProductDto>[] = [
    { label: 'Edit', icon: Pencil, onClick: (row) => setForm(toForm(row)) },
    {
      label: 'Delete',
      icon: Trash2,
      danger: true,
      onClick: (row) => setToDelete(row),
    },
  ];

  const hasVariants = (form?.variants.some((v) => v.label.trim() !== '')) ?? false;

  const priceValid = !!form && form.price.trim() !== '' && Number.isFinite(Number(form.price));
  const productSteps: WizardStep[] = form ? [
    {
      id: 'details',
      title: 'Details',
      description: 'Name, price, category',
      error: form.name.trim() === '' || !priceValid,
      complete: form.name.trim() !== '' && priceValid,
      content: (
        <>
          <div className="grid gap-2">
            <Label htmlFor="p-name">Name</Label>
            <Input id="p-name" value={form.name} onChange={(e) => patch({ name: e.target.value })} />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="grid gap-2">
              <Label htmlFor="p-price">Price (₹)</Label>
              <Input id="p-price" type="number" value={form.price} onChange={(e) => patch({ price: e.target.value })} />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="p-category">Category</Label>
              <Input id="p-category" value={form.category} onChange={(e) => patch({ category: e.target.value })} />
            </div>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="p-desc">Description</Label>
            <Textarea id="p-desc" value={form.description} onChange={(e) => patch({ description: e.target.value })} />
          </div>

          {/* Special features — short highlight bullets shown on the product detail page. */}
          <div className="space-y-2 rounded-md border p-3">
            <div className="flex items-center justify-between">
              <Label>Special features</Label>
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => patch({ features: [...form.features, ''] })}
              >
                Add feature
              </Button>
            </div>
            {form.features.length === 0 && (
              <p className="text-xs text-muted-foreground">
                Optional highlight bullets (e.g. “Water-resistant”, “2-year warranty”).
              </p>
            )}
            {form.features.map((feat, i) => (
              <div key={i} className="flex items-center gap-2">
                <Input
                  placeholder={`Feature ${i + 1}`}
                  value={feat}
                  onChange={(e) => patch({ features: form.features.map((x, j) => (j === i ? e.target.value : x)) })}
                />
                <Button
                  type="button"
                  size="icon"
                  variant="ghost"
                  className="shrink-0 text-destructive"
                  aria-label="Remove feature"
                  onClick={() => patch({ features: form.features.filter((_, j) => j !== i) })}
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
            ))}
          </div>

          <div className="flex items-center justify-between">
            <Label htmlFor="p-active">Active</Label>
            <Switch id="p-active" checked={form.active} onCheckedChange={(v) => patch({ active: v })} />
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
      id: 'fulfilment',
      title: 'Fulfilment',
      description: 'Workflow & branches',
      content: (
        <>
          <div className="grid gap-2">
            <Label htmlFor="p-workflow">Workflow</Label>
            <Select
              value={form.workflowCode || DEFAULT_WORKFLOW}
              onValueChange={(v) => patch({ workflowCode: v === DEFAULT_WORKFLOW ? '' : v })}
            >
              <SelectTrigger id="p-workflow"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value={DEFAULT_WORKFLOW}>Default</SelectItem>
                {(workflows ?? []).map((w) => (
                  <SelectItem key={w.code} value={w.code}>
                    {w.label}{w.default ? ' (default)' : ''}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-2">
            <Label>Branch availability</Label>
            <Select value={form.branchScope} onValueChange={(v) => patch({ branchScope: v as BranchScope })}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="GLOBAL">All branches</SelectItem>
                <SelectItem value="SELECTED">Selected branches</SelectItem>
              </SelectContent>
            </Select>
            {form.branchScope === 'SELECTED' && (
              (branches ?? []).length === 0 ? (
                <p className="text-xs text-muted-foreground">No branches yet — create one under Branches first.</p>
              ) : (
                <div className="space-y-2 rounded-md border p-3">
                  {(branches ?? []).map((b) => {
                    const selected = parseIds(form.availableBranchIds);
                    const checked = selected.includes(b.id);
                    const toggle = (on: boolean) => {
                      const next = on ? [...selected, b.id] : selected.filter((id) => id !== b.id);
                      patch({ availableBranchIds: next.join(', ') });
                    };
                    return (
                      <label key={b.id} className="flex items-center gap-2 text-sm">
                        <Checkbox checked={checked} onCheckedChange={(v) => toggle(v === true)} />
                        <span>{b.name}</span>
                      </label>
                    );
                  })}
                </div>
              )
            )}
          </div>
        </>
      ),
    },
    {
      id: 'inventory',
      title: 'Inventory',
      description: 'Stock & variants',
      content: (
        <>
          {/* Stock (hidden when the product has variants — stock is then per-variant) */}
          {!hasVariants && (
            <div className="space-y-3 rounded-md border p-3">
              <div className="flex items-center justify-between">
                <Label htmlFor="p-track">Track stock</Label>
                <Switch id="p-track" checked={form.stockTracked} onCheckedChange={(v) => patch({ stockTracked: v })} />
              </div>
              {form.stockTracked && (
                <>
                  <div className="flex items-center justify-between">
                    <Label htmlFor="p-perbranch">Stock per branch</Label>
                    <Switch id="p-perbranch" checked={form.stockPerBranch} onCheckedChange={(v) => patch({ stockPerBranch: v })} />
                  </div>
                  {form.stockPerBranch ? (
                    <p className="text-xs text-muted-foreground">
                      Per-branch stock is entered once branches exist (branches feature).
                    </p>
                  ) : (
                    <div className="grid gap-2">
                      <Label htmlFor="p-stock">Stock</Label>
                      <Input id="p-stock" type="number" value={form.stock} onChange={(e) => patch({ stock: e.target.value })} />
                    </div>
                  )}
                </>
              )}
            </div>
          )}

          {/* Variants / sizes */}
          <div className="space-y-2 rounded-md border p-3">
            <div className="flex items-center justify-between">
              <Label>Variants / sizes</Label>
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => patch({
                  variants: [...form.variants, { id: null, label: '', sku: '', price: '', stockTracked: false, stock: '' }],
                })}
              >
                Add variant
              </Button>
            </div>
            {form.variants.length === 0 && (
              <p className="text-xs text-muted-foreground">
                No variants — the product is added to the cart in one click.
              </p>
            )}
            {form.variants.map((v, i) => {
              const setV = (p: Partial<VariantForm>) =>
                patch({ variants: form.variants.map((x, j) => (j === i ? { ...x, ...p } : x)) });
              return (
                <div key={i} className="space-y-2 rounded border p-2">
                  <div className="grid grid-cols-3 gap-2">
                    <Input placeholder="Label (e.g. M)" value={v.label} onChange={(e) => setV({ label: e.target.value })} />
                    <Input placeholder="SKU" value={v.sku} onChange={(e) => setV({ sku: e.target.value })} />
                    <Input placeholder="Price override" type="number" value={v.price} onChange={(e) => setV({ price: e.target.value })} />
                  </div>
                  <div className="flex items-center gap-4">
                    <div className="flex items-center gap-2">
                      <Switch checked={v.stockTracked} onCheckedChange={(val) => setV({ stockTracked: val })} />
                      <span className="text-sm">Track stock</span>
                    </div>
                    {v.stockTracked && !form.stockPerBranch && (
                      <Input placeholder="Stock" type="number" className="w-28" value={v.stock} onChange={(e) => setV({ stock: e.target.value })} />
                    )}
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      className="ml-auto text-destructive"
                      onClick={() => patch({ variants: form.variants.filter((_, j) => j !== i) })}
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
        <h1 className="text-2xl font-bold">Products</h1>
        <div className="flex gap-2">
          <BulkUploadDialog
            title="Bulk upload products"
            columns={['name', 'price', 'category', 'stockTracked', 'stock', 'workflowCode', 'branchSlugs']}
            sampleRow={['Widget', '199', 'Gadgets', 'true', '10', 'default', '']}
            templateName="products-template.csv"
            onUpload={(file) => productService.bulkUpload(file)}
          />
          <Button onClick={() => setForm(emptyForm())}>New product</Button>
        </div>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <AdminDataGrid<ProductDto>
          rowData={products ?? []}
          columnDefs={columnDefs}
          actions={actions}
          emptyMessage="No products."
          testId="admin-products-grid"
        />
      )}

      <StepperSheet
        open={form != null}
        onOpenChange={(next) => !next && setForm(null)}
        title={form?.id == null ? 'New product' : `Edit — ${form?.name}`}
        steps={productSteps}
        canSave={canSave}
        saving={saving}
        onSave={save}
      />

      <ConfirmDialog
        open={toDelete != null}
        onOpenChange={() => setToDelete(null)}
        title="Delete product?"
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
