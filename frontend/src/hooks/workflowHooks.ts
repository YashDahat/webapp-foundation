// React Query hooks over the admin workflow + connectors SDK. Mutations wrap withToast.
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { connectorService, workflowService } from '@/services/workflowService';
import { withToast } from '@/lib/apiToast';
import type { ConnectorDto, WorkflowDto } from '@/types/order';

export function useWorkflows() {
  return useQuery<WorkflowDto[]>({ queryKey: ['admin-workflows'], queryFn: workflowService.list });
}

export function useReplaceWorkflow() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ code, dto }: { code: string; dto: WorkflowDto }) =>
      withToast(workflowService.replace(code, dto), 'Workflow saved'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-workflows'] }),
  });
}

export function useConnectors() {
  return useQuery<ConnectorDto[]>({ queryKey: ['admin-connectors'], queryFn: connectorService.list });
}

export function useUpdateConnector() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: string; req: { enabled?: boolean; settings?: Record<string, string> } }) =>
      withToast(connectorService.update(id, req), 'Connector updated'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-connectors'] }),
  });
}
