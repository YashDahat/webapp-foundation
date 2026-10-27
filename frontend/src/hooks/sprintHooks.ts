// React Query hooks over the sprint SDK. Mutations wrap withToast; failures auto-toast via the interceptor.
// Sprint writes also invalidate the board (completing a sprint re-points carried-forward orders).
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { sprintService } from '@/services/sprintService';
import { withToast } from '@/lib/apiToast';
import type { SprintDto, UpsertSprintRequest, CompleteSprintRequest } from '@/types/sprint';

export function useSprints(workflowCode: string | null) {
  return useQuery<SprintDto[]>({
    queryKey: ['sprints', workflowCode],
    queryFn: () => sprintService.list(workflowCode as string),
    enabled: !!workflowCode,
  });
}

function invalidate(qc: ReturnType<typeof useQueryClient>, workflowCode: string) {
  qc.invalidateQueries({ queryKey: ['sprints', workflowCode] });
  qc.invalidateQueries({ queryKey: ['order-board'] });
}

export function useCreateSprint() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: UpsertSprintRequest) => withToast(sprintService.create(req), 'Sprint created'),
    onSuccess: (s) => invalidate(qc, s.workflowCode),
  });
}

export function useUpdateSprint() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: UpsertSprintRequest }) =>
      withToast(sprintService.update(id, req), 'Sprint saved'),
    onSuccess: (s) => invalidate(qc, s.workflowCode),
  });
}

export function useCompleteSprint() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: CompleteSprintRequest }) =>
      withToast(sprintService.complete(id, req), 'Sprint completed'),
    onSuccess: (s) => invalidate(qc, s.workflowCode),
  });
}
