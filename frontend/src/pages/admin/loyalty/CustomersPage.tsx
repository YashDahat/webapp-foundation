import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { useLoyaltyCustomers } from '@/hooks/loyaltyHooks';
import type { LoyaltyProfileDto } from '@/types/loyalty';

/** Read-only grid of customer loyalty state: tier, Loyalty Score, coin balance and the engine's stats. */
export default function LoyaltyCustomersPage() {
  const { data: customers, isLoading } = useLoyaltyCustomers();

  const columnDefs: ColDef<LoyaltyProfileDto>[] = [
    { headerName: 'Customer', valueGetter: (p) => p.data?.userName || p.data?.userEmail || `#${p.data?.userId}`, flex: 1.4 },
    { field: 'tier', maxWidth: 120 },
    { field: 'score', headerName: 'Score', valueFormatter: (p) => (p.value != null ? Number(p.value).toFixed(0) : '0'), maxWidth: 110 },
    { field: 'coinBalance', headerName: 'Coins', valueFormatter: (p) => (p.value != null ? Number(p.value).toFixed(0) : '0'), maxWidth: 110 },
    { field: 'avgOrderValue', headerName: 'AOV', valueFormatter: (p) => (p.value != null ? `₹${Number(p.value).toFixed(0)}` : '₹0'), maxWidth: 120 },
    { field: 'returnRate', headerName: 'Return %', valueFormatter: (p) => (p.value != null ? `${Math.round(Number(p.value) * 100)}%` : '0%'), maxWidth: 120 },
    { field: 'inHoldout', headerName: 'Holdout', valueFormatter: (p) => (p.value ? 'Yes' : 'No'), maxWidth: 110 },
    { headerName: 'Last order', valueGetter: (p) => (p.data?.lastOrderAt ? new Date(p.data.lastOrderAt).toLocaleDateString() : '—'), maxWidth: 140 },
  ];

  return (
    <div className="space-y-4">
      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <AdminDataGrid<LoyaltyProfileDto>
          rowData={customers ?? []}
          columnDefs={columnDefs}
          emptyMessage="No customers have a loyalty profile yet."
          testId="loyalty-customers-grid"
        />
      )}
    </div>
  );
}
