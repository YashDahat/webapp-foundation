// React Query hooks over the support SDK. Mutations wrap withToast for success feedback; request
// failures auto-toast via the apiClient interceptor.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { supportService } from '@/services/supportService';
import { withToast } from '@/lib/apiToast';
import type {
  AdminReplyRequest,
  CreateSupportCaseRequest,
  PostMessageRequest,
  SupportCaseDto,
  SupportThreadDto,
} from '@/types/support';

// ── Customer ──
export function useMyCases() {
  return useQuery<SupportCaseDto[]>({ queryKey: ['support', 'mine'], queryFn: supportService.myCases });
}

export function useSupportThread(id: number | null) {
  return useQuery<SupportThreadDto>({
    queryKey: ['support', 'thread', id],
    queryFn: () => supportService.thread(id as number),
    enabled: id != null,
  });
}

export function useOpenCase() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateSupportCaseRequest) => withToast(supportService.open(req), 'Support request submitted'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['support', 'mine'] }),
  });
}

export function usePostMessage(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: PostMessageRequest) => withToast(supportService.reply(id, req), 'Reply sent'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['support', 'thread', id] });
      qc.invalidateQueries({ queryKey: ['support', 'mine'] });
    },
  });
}

// ── Admin helpdesk ──
export function useAllSupportCases() {
  return useQuery<SupportCaseDto[]>({ queryKey: ['support', 'all'], queryFn: supportService.all });
}

export function useAdminSupportThread(id: number | null) {
  return useQuery<SupportThreadDto>({
    queryKey: ['support', 'admin-thread', id],
    queryFn: () => supportService.adminThread(id as number),
    enabled: id != null,
  });
}

export function useAdminReply(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: AdminReplyRequest) => withToast(supportService.adminReply(id, req), 'Reply sent'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['support', 'admin-thread', id] });
      qc.invalidateQueries({ queryKey: ['support', 'all'] });
    },
  });
}
