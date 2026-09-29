import { useState } from 'react';
import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { type RowAction } from '@/components/admin/RowActionsCell';
import { SupportThreadPanel } from '@/components/admin/support/SupportThreadPanel';
import { useAllSupportCases } from '@/hooks/supportHooks';
import type { SupportCaseDto } from '@/types/support';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

const STATUS_LABEL: Record<string, string> = {
  OPEN: 'Open',
  IN_PROGRESS: 'In progress',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
};

/** Admin helpdesk: an AdminDataGrid of all support cases; open a case to view/respond in a thread panel. */
export default function AdminSupportPage() {
  const { data: cases, isLoading } = useAllSupportCases();
  const [openId, setOpenId] = useState<number | null>(null);

  const columnDefs: ColDef<SupportCaseDto>[] = [
    { field: 'id', headerName: '#', maxWidth: 90, flex: 0 },
    { field: 'subject', headerName: 'Subject', flex: 2 },
    { field: 'customerName', headerName: 'Customer' },
    { field: 'customerEmail', headerName: 'Email', flex: 1.4 },
    {
      field: 'status',
      headerName: 'Status',
      valueFormatter: (p) => STATUS_LABEL[p.value as string] ?? (p.value as string),
    },
    {
      field: 'updatedAt',
      headerName: 'Updated',
      valueFormatter: (p) => (p.value ? new Date(p.value as string).toLocaleString() : ''),
    },
  ];

  const actions: RowAction<SupportCaseDto>[] = [
    { label: 'Open thread', onClick: (row) => setOpenId(row.id) },
  ];

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Support</h1>
      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <AdminDataGrid<SupportCaseDto>
          rowData={cases ?? []}
          columnDefs={columnDefs}
          actions={actions}
          emptyMessage="No support cases yet."
          testId="admin-support-grid"
        />
      )}

      <Dialog open={openId != null} onOpenChange={(next) => !next && setOpenId(null)}>
        <DialogContent className="sm:max-w-[640px]">
          <DialogHeader>
            <DialogTitle>Support thread</DialogTitle>
          </DialogHeader>
          {openId != null && <SupportThreadPanel caseId={openId} />}
        </DialogContent>
      </Dialog>
    </div>
  );
}
