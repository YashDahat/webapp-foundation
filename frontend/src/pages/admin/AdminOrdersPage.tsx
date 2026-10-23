import { useMemo, useState } from 'react';
import { BadgeCheck, Ban, ChevronRight } from 'lucide-react';
import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { type RowAction } from '@/components/admin/RowActionsCell';
import { BulkUploadDialog } from '@/components/admin/BulkUploadDialog';
import { orderService } from '@/services/orderService';
import {
  useAdminOrders, useAdvanceStage, useAdminCancelOrder, useMarkPaid,
} from '@/hooks/orderHooks';
import { useWorkflows } from '@/hooks/workflowHooks';
import type { OrderDto } from '@/types/order';
import {
  Sheet, SheetContent, SheetHeader, SheetTitle, SheetFooter,
} from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { ConfirmDialog } from '@/components/ConfirmDialog';

/** Admin Orders: AdminDataGrid with advance-stage, admin cancel (override), mark-paid, and bulk upload. */
export default function AdminOrdersPage() {
  const { data: orders, isLoading } = useAdminOrders();
  const { data: workflows } = useWorkflows();
  const advance = useAdvanceStage();
  const cancel = useAdminCancelOrder();
  const markPaid = useMarkPaid();

  const [advancing, setAdvancing] = useState<OrderDto | null>(null);
  const [stageCode, setStageCode] = useState('');
  const [toCancel, setToCancel] = useState<OrderDto | null>(null);

  // workflowCode → ordered stage codes (for the advance picker).
  const stagesByWorkflow = useMemo(() => {
    const map: Record<string, string[]> = {};
    (workflows ?? []).forEach((w) => { map[w.code] = w.stages.map((s) => s.code); });
    return map;
  }, [workflows]);

  const openAdvance = (order: OrderDto) => {
    setAdvancing(order);
    setStageCode(order.currentStageCode ?? '');
  };

  const columnDefs: ColDef<OrderDto>[] = [
    { field: 'orderNumber', headerName: 'Order', flex: 1.2 },
    { headerName: 'Items', valueGetter: (p) => p.data?.items.length ?? 0, maxWidth: 100 },
    { field: 'total', valueFormatter: (p) => `₹${p.value}`, maxWidth: 120 },
    { field: 'fulfillmentType', headerName: 'Fulfilment' },
    { field: 'currentStageCode', headerName: 'Stage' },
    { field: 'paymentState', headerName: 'Payment' },
    { headerName: 'Placed', valueGetter: (p) => (p.data ? new Date(p.data.createdAt).toLocaleDateString() : ''), maxWidth: 130 },
  ];

  const actions: RowAction<OrderDto>[] = [
    { label: 'Advance stage', icon: ChevronRight, onClick: openAdvance },
    {
      label: 'Mark paid',
      icon: BadgeCheck,
      onClick: (row) => markPaid.mutate(row.id),
      hidden: (row) => row.paymentState !== 'AWAITING_PAYMENT',
    },
    {
      label: 'Cancel (override)',
      icon: Ban,
      danger: true,
      onClick: (row) => setToCancel(row),
    },
  ];

  const stageOptions = advancing ? (stagesByWorkflow[advancing.workflowCode] ?? []) : [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Orders</h1>
        <BulkUploadDialog
          title="Bulk upload orders"
          columns={['userId', 'itemType', 'itemId', 'quantity']}
          sampleRow={['1', 'PRODUCT', '1', '2']}
          templateName="orders-template.csv"
          onUpload={(file) => orderService.bulkUpload(file)}
        />
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <AdminDataGrid<OrderDto>
          rowData={orders ?? []}
          columnDefs={columnDefs}
          actions={actions}
          emptyMessage="No orders."
          testId="admin-orders-grid"
        />
      )}

      <Sheet open={advancing != null} onOpenChange={(next) => !next && setAdvancing(null)}>
        <SheetContent side="right" className="flex flex-col">
          <SheetHeader>
            <SheetTitle>Advance {advancing?.orderNumber}</SheetTitle>
          </SheetHeader>
          <div className="flex-1 py-4">
            <Select value={stageCode} onValueChange={setStageCode}>
              <SelectTrigger><SelectValue placeholder="Select a stage" /></SelectTrigger>
              <SelectContent>
                {stageOptions.map((c) => <SelectItem key={c} value={c}>{c}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>
          <SheetFooter>
            <Button variant="outline" onClick={() => setAdvancing(null)}>Cancel</Button>
            <Button
              disabled={advance.isPending || !stageCode || stageCode === advancing?.currentStageCode}
              onClick={() => advancing && advance.mutate({ id: advancing.id, stageCode }, { onSuccess: () => setAdvancing(null) })}
            >
              {advance.isPending ? 'Saving…' : 'Advance'}
            </Button>
          </SheetFooter>
        </SheetContent>
      </Sheet>

      <ConfirmDialog
        open={toCancel != null}
        onOpenChange={() => setToCancel(null)}
        title="Cancel order?"
        description={<>This will cancel order “{toCancel?.orderNumber}” (admin override). This action cannot be undone.</>}
        confirmLabel="Cancel order"
        pendingLabel="Cancelling…"
        cancelLabel="Keep order"
        pending={cancel.isPending}
        destructive
        onConfirm={() => toCancel && cancel.mutate({ id: toCancel.id, reason: 'Admin cancellation' }, { onSuccess: () => setToCancel(null) })}
      />
    </div>
  );
}
