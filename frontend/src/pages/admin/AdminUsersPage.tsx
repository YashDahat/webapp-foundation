import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColDef } from 'ag-grid-community';
import { AdminDataGrid } from '@/components/admin/AdminDataGrid';
import { type RowAction } from '@/components/admin/RowActionsCell';
import { BulkUploadDialog } from '@/components/admin/BulkUploadDialog';
import { userService } from '@/services/userService';
import { withToast } from '@/lib/apiToast';
import { useAuth } from '@/context/AuthContext';
import type { UserDto } from '@/types/user';
import type { Role } from '@/types/auth';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';

const ROLES: Role[] = ['USER', 'ADMIN', 'SUPER_ADMIN'];

/** Admin Users: an AdminDataGrid of all users with role change, delete (super-admin only), and bulk upload. */
export default function AdminUsersPage() {
  const { user } = useAuth();
  const isSuperAdmin = user?.role === 'SUPER_ADMIN';
  const qc = useQueryClient();
  const { data: users, isLoading } = useQuery<UserDto[]>({ queryKey: ['admin-users'], queryFn: userService.list });

  const [editing, setEditing] = useState<UserDto | null>(null);
  const [role, setRole] = useState<Role>('USER');

  const refresh = () => qc.invalidateQueries({ queryKey: ['admin-users'] });

  const changeRole = useMutation({
    mutationFn: ({ id, role }: { id: number; role: Role }) => withToast(userService.changeRole(id, role), 'Role updated'),
    onSuccess: () => { setEditing(null); refresh(); },
  });
  const remove = useMutation({
    mutationFn: (id: number) => withToast(userService.remove(id), 'User deleted'),
    onSuccess: refresh,
  });

  const columnDefs: ColDef<UserDto>[] = [
    { field: 'id', headerName: '#', maxWidth: 90, flex: 0 },
    { headerName: 'Name', valueGetter: (p) => `${p.data?.firstName ?? ''} ${p.data?.lastName ?? ''}`.trim() },
    { field: 'email', flex: 1.4 },
    { field: 'phone' },
    { field: 'role' },
    { field: 'emailVerified', headerName: 'Verified', valueFormatter: (p) => (p.value ? 'Yes' : 'No'), maxWidth: 110 },
    { field: 'twoFactorEnabled', headerName: '2FA', valueFormatter: (p) => (p.value ? 'On' : 'Off'), maxWidth: 90 },
  ];

  const actions: RowAction<UserDto>[] = [
    { label: 'Change role', onClick: (row) => { setEditing(row); setRole(row.role); } },
    {
      label: 'Delete',
      danger: true,
      onClick: (row) => { if (confirm(`Delete ${row.email}?`)) remove.mutate(row.id); },
      // Only the super admin can delete; never offer delete for a super admin or for yourself.
      hidden: (row) => !isSuperAdmin || row.role === 'SUPER_ADMIN' || row.email === user?.username,
    },
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Users</h1>
        <BulkUploadDialog
          title="Bulk upload users"
          columns={['firstName', 'lastName', 'email', 'phone', 'password', 'role']}
          sampleRow={['Aarav', 'Sharma', 'aarav@example.com', '+919000000000', 'Passw0rd123', 'USER']}
          templateName="users-template.csv"
          onUpload={(file) => userService.bulkUpload(file).then((r) => { refresh(); return r; })}
        />
      </div>

      {isLoading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : (
        <AdminDataGrid<UserDto>
          rowData={users ?? []}
          columnDefs={columnDefs}
          actions={actions}
          emptyMessage="No users."
          testId="admin-users-grid"
        />
      )}

      <Dialog open={editing != null} onOpenChange={(next) => !next && setEditing(null)}>
        <DialogContent className="sm:max-w-[420px]">
          <DialogHeader>
            <DialogTitle>Change role — {editing?.email}</DialogTitle>
          </DialogHeader>
          <Select value={role} onValueChange={(v) => setRole(v as Role)}>
            <SelectTrigger><SelectValue /></SelectTrigger>
            <SelectContent>
              {ROLES.map((r) => <SelectItem key={r} value={r}>{r}</SelectItem>)}
            </SelectContent>
          </Select>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditing(null)}>Cancel</Button>
            <Button
              disabled={changeRole.isPending || !editing || role === editing.role}
              onClick={() => editing && changeRole.mutate({ id: editing.id, role })}
            >
              {changeRole.isPending ? 'Saving…' : 'Save'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
