// FOUNDATION-owned admin workflow + connectors SDK (com.webappfoundation AdminWorkflowController +
// AdminConnectorController). ADMIN-gated.
import apiClient from '@/api/client';
import type { ConnectorDto, WorkflowDto } from '@/types/order';

export const workflowService = {
  /** GET /api/v1/admin/workflow — all workflows with stages + bindings. */
  list: async (): Promise<WorkflowDto[]> => {
    const { data } = await apiClient.get<WorkflowDto[]>('/api/v1/admin/workflow');
    return data;
  },
  /** PUT /api/v1/admin/workflow/{code} — replace a workflow's stages + bindings. */
  replace: async (code: string, dto: WorkflowDto): Promise<WorkflowDto[]> => {
    const { data } = await apiClient.put<WorkflowDto[]>(`/api/v1/admin/workflow/${code}`, dto);
    return data;
  },
};

export const connectorService = {
  /** GET /api/v1/admin/connectors — registered connectors with declared actions + settings. */
  list: async (): Promise<ConnectorDto[]> => {
    const { data } = await apiClient.get<ConnectorDto[]>('/api/v1/admin/connectors');
    return data;
  },
  /** PUT /api/v1/admin/connectors/{id} — toggle enabled / edit non-secret settings. */
  update: async (id: string, req: { enabled?: boolean; settings?: Record<string, string> }): Promise<ConnectorDto> => {
    const { data } = await apiClient.put<ConnectorDto>(`/api/v1/admin/connectors/${id}`, req);
    return data;
  },
};
