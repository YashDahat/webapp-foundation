// FOUNDATION-owned order-board sprint SDK (com.webappfoundation AdminSprintController). ADMIN-gated
// (sprints are NOT part of the STAFF allowlist). Sprints are time-boxed batches per workflow.
import apiClient from '@/api/client';
import type { SprintDto, UpsertSprintRequest, CompleteSprintRequest } from '@/types/sprint';

export const sprintService = {
  /** GET /api/v1/admin/sprints?workflowCode= — sprints for a workflow, newest first. */
  list: async (workflowCode: string): Promise<SprintDto[]> => {
    const { data } = await apiClient.get<SprintDto[]>('/api/v1/admin/sprints', { params: { workflowCode } });
    return data;
  },
  /** POST /api/v1/admin/sprints — create a new ACTIVE sprint (409 if one is already active). */
  create: async (req: UpsertSprintRequest): Promise<SprintDto> => {
    const { data } = await apiClient.post<SprintDto>('/api/v1/admin/sprints', req);
    return data;
  },
  /** PUT /api/v1/admin/sprints/{id} — edit a sprint's name/dates only. */
  update: async (id: number, req: UpsertSprintRequest): Promise<SprintDto> => {
    const { data } = await apiClient.put<SprintDto>(`/api/v1/admin/sprints/${id}`, req);
    return data;
  },
  /** POST /api/v1/admin/sprints/{id}/complete — close the sprint, open the next, carry forward ticked orders. */
  complete: async (id: number, req: CompleteSprintRequest): Promise<SprintDto> => {
    const { data } = await apiClient.post<SprintDto>(`/api/v1/admin/sprints/${id}/complete`, req);
    return data;
  },
};
