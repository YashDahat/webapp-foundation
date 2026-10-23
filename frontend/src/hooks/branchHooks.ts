// React Query hooks over the branch SDK. Mutations wrap withToast; failures auto-toast via the interceptor.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { branchService } from '@/services/branchService';
import { withToast } from '@/lib/apiToast';
import type { BranchDto, UpsertBranchRequest } from '@/types/branch';

export function useBranches() {
  return useQuery<BranchDto[]>({ queryKey: ['branches'], queryFn: branchService.list });
}

export function useAdminBranches() {
  return useQuery<BranchDto[]>({ queryKey: ['admin-branches'], queryFn: branchService.adminList });
}

export function useCreateBranch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: UpsertBranchRequest) => withToast(branchService.create(req), 'Branch created'),
    onSuccess: () => invalidate(qc),
  });
}

export function useUpdateBranch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: UpsertBranchRequest }) =>
      withToast(branchService.update(id, req), 'Branch updated'),
    onSuccess: () => invalidate(qc),
  });
}

export function useDeleteBranch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => withToast(branchService.remove(id), 'Branch deleted'),
    onSuccess: () => invalidate(qc),
  });
}

function invalidate(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: ['admin-branches'] });
  qc.invalidateQueries({ queryKey: ['branches'] });
}
