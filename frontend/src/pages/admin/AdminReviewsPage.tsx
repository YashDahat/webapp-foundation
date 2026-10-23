import { useState } from 'react';
import { Trash2 } from 'lucide-react';
import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { type RowAction } from '@/components/admin/RowActionsCell';
import { useAdminDeleteReview, useAdminReviews } from '@/hooks/reviewHooks';
import type { ProductReviewDto } from '@/types/review';
import { ConfirmDialog } from '@/components/ConfirmDialog';

/** Admin Reviews: an AdminDataGrid of all product reviews (newest first) with moderate (delete). No bulk-upload. */
export default function AdminReviewsPage() {
  const { data: reviews, isLoading } = useAdminReviews();
  const remove = useAdminDeleteReview();
  const [toDelete, setToDelete] = useState<ProductReviewDto | null>(null);

  const columnDefs: ColDef<ProductReviewDto>[] = [
    { headerName: 'Product', valueGetter: (p) => p.data?.productName ?? `#${p.data?.productId}`, flex: 1.4 },
    { field: 'userName', headerName: 'Reviewer', flex: 1 },
    {
      headerName: 'Rating',
      valueGetter: (p) => (p.data ? '★'.repeat(p.data.rating) + '☆'.repeat(5 - p.data.rating) : ''),
      maxWidth: 140,
    },
    { field: 'comment', flex: 2 },
    {
      headerName: 'Submitted',
      valueGetter: (p) => (p.data ? new Date(p.data.createdAt).toLocaleString() : ''),
      maxWidth: 200,
    },
  ];

  const actions: RowAction<ProductReviewDto>[] = [
    { label: 'Delete', icon: Trash2, danger: true, onClick: (row) => setToDelete(row) },
  ];

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Reviews</h1>
      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <AdminDataGrid<ProductReviewDto>
          rowData={reviews ?? []}
          columnDefs={columnDefs}
          actions={actions}
          emptyMessage="No reviews yet."
          testId="admin-reviews-grid"
        />
      )}

      <ConfirmDialog
        open={toDelete != null}
        onOpenChange={() => setToDelete(null)}
        title="Delete review?"
        description="This will permanently delete this review. This action cannot be undone."
        confirmLabel="Delete"
        pendingLabel="Deleting…"
        pending={remove.isPending}
        destructive
        onConfirm={() => toDelete && remove.mutate(toDelete.id, { onSuccess: () => setToDelete(null) })}
      />
    </div>
  );
}
