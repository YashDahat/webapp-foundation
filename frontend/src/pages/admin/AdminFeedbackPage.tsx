import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { useAdminFeedback } from '@/hooks/feedbackHooks';
import type { OrderFeedbackDto } from '@/types/feedback';

/** Admin Feedback: a read-only AdminDataGrid of submitted ratings (like /admin/logs). No bulk-upload. */
export default function AdminFeedbackPage() {
  const { data: feedback, isLoading } = useAdminFeedback();

  const columnDefs: ColDef<OrderFeedbackDto>[] = [
    { headerName: 'Order', valueGetter: (p) => p.data?.orderNumber ?? `#${p.data?.orderId}`, flex: 1 },
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

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Feedback</h1>
      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <AdminDataGrid<OrderFeedbackDto>
          rowData={feedback ?? []}
          columnDefs={columnDefs}
          emptyMessage="No feedback yet."
          testId="admin-feedback-grid"
        />
      )}
    </div>
  );
}
