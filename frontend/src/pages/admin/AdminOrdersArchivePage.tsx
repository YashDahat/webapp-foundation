import { useMemo, useState } from 'react';
import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { useOrderArchive } from '@/hooks/orderHooks';
import { useWorkflows } from '@/hooks/workflowHooks';
import type { OrderArchiveRowDto } from '@/types/order';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';

const ALL = '__all__';

/**
 * Admin "All orders" archive — a flat, cross-sprint / cross-workflow record of every order ever placed,
 * complementing the sprint-scoped board. Read-only grid (AdminDataGrid) with workflow / sprint / status
 * filters + search. ADMIN-only (routed under RequireAdmin; the archive endpoint is ADMIN-gated server-side).
 */
export default function AdminOrdersArchivePage() {
  const { data: rows, isLoading } = useOrderArchive();
  const { data: workflows } = useWorkflows();

  const [workflowCode, setWorkflowCode] = useState(ALL);
  const [sprint, setSprint] = useState(ALL);
  const [status, setStatus] = useState(ALL);
  const [search, setSearch] = useState('');

  // Sprint filter options are the distinct sprint names present in the data (newest-first order preserved).
  const sprintNames = useMemo(() => {
    const seen = new Set<string>();
    (rows ?? []).forEach((r) => { if (r.sprintName) seen.add(r.sprintName); });
    return [...seen];
  }, [rows]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return (rows ?? []).filter((r) => {
      if (workflowCode !== ALL && r.workflowCode !== workflowCode) return false;
      if (sprint !== ALL && r.sprintName !== sprint) return false;
      if (status !== ALL) {
        if (status === 'BLOCKED' ? !r.blocked : r.paymentState !== status) return false;
      }
      if (q && !r.orderNumber.toLowerCase().includes(q) && !(r.customerName ?? '').toLowerCase().includes(q)) {
        return false;
      }
      return true;
    });
  }, [rows, workflowCode, sprint, status, search]);

  const columnDefs: ColDef<OrderArchiveRowDto>[] = [
    { field: 'orderNumber', headerName: 'Order', flex: 1.1, sortable: true },
    { field: 'customerName', headerName: 'Customer', flex: 1.2 },
    { field: 'workflowLabel', headerName: 'Workflow', flex: 1 },
    { field: 'sprintName', headerName: 'Sprint', flex: 0.9, valueFormatter: (p) => p.value ?? '—' },
    {
      headerName: 'Stage',
      flex: 1,
      valueGetter: (p) => (p.data?.blocked ? `${p.data.stageLabel} (blocked)` : p.data?.stageLabel ?? ''),
    },
    { field: 'paymentState', headerName: 'Payment', flex: 0.9 },
    { field: 'fulfillmentType', headerName: 'Fulfilment', flex: 0.9 },
    { field: 'branchName', headerName: 'Branch', flex: 0.9, valueFormatter: (p) => p.value ?? '—' },
    {
      field: 'total', headerName: 'Total', maxWidth: 120, sortable: true,
      valueFormatter: (p) => `${p.data?.currency === 'INR' ? '₹' : ''}${p.value}`,
    },
    {
      headerName: 'Placed', maxWidth: 150, sortable: true,
      field: 'createdAt',
      valueFormatter: (p) => (p.value ? new Date(p.value).toLocaleDateString() : ''),
    },
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <h1 className="text-2xl font-bold">All orders</h1>
        <Badge variant="secondary">{filtered.length}</Badge>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <Input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search order / customer…"
          className="w-56"
          data-testid="archive-search"
        />
        <Select value={workflowCode} onValueChange={setWorkflowCode}>
          <SelectTrigger className="w-44"><SelectValue placeholder="Workflow" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All workflows</SelectItem>
            {(workflows ?? []).map((w) => <SelectItem key={w.code} value={w.code}>{w.label}</SelectItem>)}
          </SelectContent>
        </Select>
        <Select value={sprint} onValueChange={setSprint}>
          <SelectTrigger className="w-44"><SelectValue placeholder="Sprint" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All sprints</SelectItem>
            {sprintNames.map((s) => <SelectItem key={s} value={s}>{s}</SelectItem>)}
          </SelectContent>
        </Select>
        <Select value={status} onValueChange={setStatus}>
          <SelectTrigger className="w-44"><SelectValue placeholder="Status" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>All statuses</SelectItem>
            <SelectItem value="AWAITING_PAYMENT">Awaiting payment</SelectItem>
            <SelectItem value="PAID">Paid</SelectItem>
            <SelectItem value="REFUND_PENDING">Refund pending</SelectItem>
            <SelectItem value="REFUNDED">Refunded</SelectItem>
            <SelectItem value="FAILED">Failed</SelectItem>
            <SelectItem value="BLOCKED">Blocked</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <AdminDataGrid<OrderArchiveRowDto>
          rowData={filtered}
          columnDefs={columnDefs}
          emptyMessage="No orders."
          testId="admin-orders-archive-grid"
        />
      )}
    </div>
  );
}
