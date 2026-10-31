// FOUNDATION-owned end-of-day log manager: the 3 figures orders can't know (COGS, operating expenses,
// complaints). An AdminDataGrid list + a right-side entry Sheet + the compulsory BulkUploadDialog.
import { useState } from 'react';
import { Pencil, Trash2, Plus } from 'lucide-react';
import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { type RowAction } from '@/components/admin/RowActionsCell';
import { BulkUploadDialog } from '@/components/admin/BulkUploadDialog';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import {
  Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription, SheetFooter,
} from '@/components/ui/sheet';
import { analyticsService } from '@/services/analyticsService';
import { useDailyLogs, useUpsertDailyLog, useDeleteDailyLog } from '@/hooks/analyticsHooks';
import type { DailyBusinessLogDto } from '@/types/analytics';

interface LogForm {
  logDate: string;
  cogs: string;
  operatingExpenses: string;
  complaints: string;
  note: string;
}

const today = () => new Date().toISOString().slice(0, 10);
const emptyForm = (): LogForm => ({ logDate: today(), cogs: '', operatingExpenses: '', complaints: '', note: '' });
const toForm = (l: DailyBusinessLogDto): LogForm => ({
  logDate: l.logDate,
  cogs: l.cogs != null ? String(l.cogs) : '',
  operatingExpenses: l.operatingExpenses != null ? String(l.operatingExpenses) : '',
  complaints: l.complaints != null ? String(l.complaints) : '',
  note: l.note ?? '',
});
const num = (s: string): number | null => (s.trim() === '' ? null : Number(s));

export function DailyLogManager(): React.JSX.Element {
  const { data: logs, isLoading } = useDailyLogs();
  const upsert = useUpsertDailyLog();
  const remove = useDeleteDailyLog();

  const [form, setForm] = useState<LogForm | null>(null);
  const [toDelete, setToDelete] = useState<DailyBusinessLogDto | null>(null);
  const patch = (p: Partial<LogForm>) => setForm((f) => (f ? { ...f, ...p } : f));

  const save = () => {
    if (!form || !form.logDate) return;
    upsert.mutate(
      {
        logDate: form.logDate,
        cogs: num(form.cogs),
        operatingExpenses: num(form.operatingExpenses),
        complaints: num(form.complaints),
        note: form.note.trim() || null,
      },
      { onSuccess: () => setForm(null) },
    );
  };

  const money = (v: number | null | undefined) => (v == null ? '—' : `₹${Math.round(v).toLocaleString('en-IN')}`);

  const columnDefs: ColDef<DailyBusinessLogDto>[] = [
    { field: 'logDate', headerName: 'Date', flex: 1 },
    { field: 'cogs', headerName: 'COGS', valueFormatter: (p) => money(p.value), flex: 1 },
    { field: 'operatingExpenses', headerName: 'Operating expenses', valueFormatter: (p) => money(p.value), flex: 1 },
    { field: 'complaints', headerName: 'Complaints', maxWidth: 130 },
    { field: 'note', headerName: 'Note', flex: 1.4 },
  ];

  const actions: RowAction<DailyBusinessLogDto>[] = [
    { label: 'Edit', icon: Pencil, onClick: (row) => setForm(toForm(row)) },
    { label: 'Delete', icon: Trash2, danger: true, onClick: (row) => setToDelete(row) },
  ];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="max-w-2xl text-sm text-muted-foreground">
          Enter a day's cost of goods sold, operating expenses and complaints. Sales, bills and customers
          are measured from orders automatically — you only fill these three.
        </p>
        <div className="flex gap-2">
          <BulkUploadDialog
            title="Bulk upload daily log"
            columns={['date', 'cogs', 'operatingExpenses', 'complaints', 'note']}
            sampleRow={['2026-10-30', '716820', '255000', '3', '']}
            templateName="daily-log-template.csv"
            onUpload={analyticsService.bulkUpload}
          />
          <Button onClick={() => setForm(emptyForm())}><Plus className="mr-1 h-4 w-4" /> Add day</Button>
        </div>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <AdminDataGrid<DailyBusinessLogDto>
          rowData={logs ?? []}
          columnDefs={columnDefs}
          actions={actions}
          emptyMessage="No daily entries yet. Add today to start tracking profit."
          testId="admin-daily-log-grid"
        />
      )}

      <Sheet open={form != null} onOpenChange={(o) => !o && setForm(null)}>
        <SheetContent className="flex flex-col">
          <SheetHeader>
            <SheetTitle>End-of-day entry</SheetTitle>
            <SheetDescription>Saving an existing date updates that day.</SheetDescription>
          </SheetHeader>
          {form && (
            <div className="flex-1 space-y-4 overflow-y-auto px-4">
              <div className="grid gap-2">
                <Label htmlFor="l-date">Date</Label>
                <Input id="l-date" type="date" value={form.logDate} onChange={(e) => patch({ logDate: e.target.value })} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="l-cogs">Cost of goods sold (₹)</Label>
                <Input id="l-cogs" type="number" inputMode="decimal" value={form.cogs} onChange={(e) => patch({ cogs: e.target.value })} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="l-opex">Operating expenses (₹)</Label>
                <Input id="l-opex" type="number" inputMode="decimal" value={form.operatingExpenses} onChange={(e) => patch({ operatingExpenses: e.target.value })} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="l-comp">Customer complaints</Label>
                <Input id="l-comp" type="number" inputMode="numeric" value={form.complaints} onChange={(e) => patch({ complaints: e.target.value })} />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="l-note">Note (optional)</Label>
                <Textarea id="l-note" value={form.note} onChange={(e) => patch({ note: e.target.value })} />
              </div>
            </div>
          )}
          <SheetFooter className="mt-auto border-t pt-4">
            <Button variant="outline" onClick={() => setForm(null)}>Cancel</Button>
            <Button onClick={save} disabled={upsert.isPending || !form?.logDate}>
              {upsert.isPending ? 'Saving…' : 'Save'}
            </Button>
          </SheetFooter>
        </SheetContent>
      </Sheet>

      <ConfirmDialog
        open={toDelete != null}
        onOpenChange={(o) => !o && setToDelete(null)}
        title="Delete entry?"
        description={toDelete ? `Remove the entry for ${toDelete.logDate}?` : ''}
        confirmLabel="Delete"
        onConfirm={() => {
          if (toDelete) remove.mutate(toDelete.id, { onSuccess: () => setToDelete(null) });
        }}
      />
    </div>
  );
}
