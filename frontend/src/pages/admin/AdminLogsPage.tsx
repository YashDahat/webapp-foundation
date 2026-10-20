import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { auditService, type AuditQuery } from '@/services/auditService';
import type { AuditEventDto } from '@/types/audit';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';

const ACTIONS = [
  'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'REGISTER', 'SIGNUP_VERIFIED', 'TWO_FACTOR_VERIFIED',
  'TWO_FACTOR_ENABLED', 'TWO_FACTOR_DISABLED', 'PASSWORD_CHANGED', 'PASSWORD_RESET_REQUESTED',
  'PASSWORD_RESET', 'USER_ROLE_CHANGED', 'USER_DELETED', 'USER_BULK_IMPORT',
];
const ANY = '__any__';

/** Admin → Logs: read-only view of the audit_log (user activity), rendered in an AdminDataGrid. */
export default function AdminLogsPage() {
  const [query, setQuery] = useState<AuditQuery>({ limit: 200 });
  const [action, setAction] = useState<string>(ANY);
  const [actorEmail, setActorEmail] = useState('');

  const { data: events, isLoading, isFetching, refetch } = useQuery<AuditEventDto[]>({
    queryKey: ['admin-audit', query],
    queryFn: () => auditService.list(query),
  });

  const apply = () => setQuery({ action: action === ANY ? undefined : action, actorEmail: actorEmail.trim() || undefined, limit: 200 });
  const clear = () => { setAction(ANY); setActorEmail(''); setQuery({ limit: 200 }); };

  const columnDefs: ColDef<AuditEventDto>[] = [
    { field: 'createdAt', headerName: 'Time', valueFormatter: (p) => (p.value ? new Date(p.value as string).toLocaleString() : ''), flex: 1.2 },
    { field: 'action', flex: 1.2 },
    { field: 'outcome', maxWidth: 110 },
    { field: 'actorEmail', headerName: 'Actor', flex: 1.3 },
    { field: 'actorRole', headerName: 'Role', maxWidth: 130 },
    { field: 'targetType', headerName: 'Target', maxWidth: 130 },
    { field: 'targetId', headerName: 'Target id', maxWidth: 120 },
    { field: 'ipAddress', headerName: 'IP', maxWidth: 140 },
    { field: 'detail', flex: 1.4 },
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Logs</h1>
        <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isFetching}>
          {isFetching ? 'Refreshing…' : 'Refresh'}
        </Button>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <div className="w-56">
          <label className="mb-1 block text-xs text-muted-foreground">Action</label>
          <Select value={action} onValueChange={setAction}>
            <SelectTrigger><SelectValue placeholder="Any action" /></SelectTrigger>
            <SelectContent>
              <SelectItem value={ANY}>Any action</SelectItem>
              {ACTIONS.map((a) => <SelectItem key={a} value={a}>{a}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
        <div className="w-64">
          <label className="mb-1 block text-xs text-muted-foreground">Actor email contains</label>
          <Input placeholder="user@example.com" value={actorEmail} onChange={(e) => setActorEmail(e.target.value)} />
        </div>
        <Button onClick={apply} data-testid="audit-apply">Apply</Button>
        <Button variant="ghost" onClick={clear}>Clear</Button>
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <AdminDataGrid<AuditEventDto>
          rowData={events ?? []}
          columnDefs={columnDefs}
          emptyMessage="No audit events."
          testId="admin-logs-grid"
        />
      )}
    </div>
  );
}
