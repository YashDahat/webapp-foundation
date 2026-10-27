// FOUNDATION-owned event SDK (com.webappfoundation EventController + AdminEventController).
// Public reads are unauthenticated; admin endpoints are ADMIN-gated.
import apiClient from '@/api/client';
import type { BulkUploadResult } from '@/types/bulkUpload';
import type { EventDto, EventFilter, UpsertEventRequest } from '@/types/event';

export const eventService = {
  // ── Public ──
  /** GET /api/v1/events — active events (soonest first), optionally filtered by category, search, branch. */
  list: async (filter: EventFilter = {}): Promise<EventDto[]> => {
    const { data } = await apiClient.get<EventDto[]>('/api/v1/events', {
      params: {
        category: filter.category || undefined,
        q: filter.q || undefined,
        branchId: filter.branchId ?? undefined,
      },
    });
    return data;
  },
  /** GET /api/v1/events/{id}. */
  get: async (id: number): Promise<EventDto> => {
    const { data } = await apiClient.get<EventDto>(`/api/v1/events/${id}`);
    return data;
  },
  /** GET /api/v1/events/slug/{slug}. */
  getBySlug: async (slug: string): Promise<EventDto> => {
    const { data } = await apiClient.get<EventDto>(`/api/v1/events/slug/${slug}`);
    return data;
  },

  // ── Admin ──
  /** GET /api/v1/admin/events — all events incl. inactive/past. */
  adminList: async (): Promise<EventDto[]> => {
    const { data } = await apiClient.get<EventDto[]>('/api/v1/admin/events');
    return data;
  },
  /** POST /api/v1/admin/events. */
  create: async (request: UpsertEventRequest): Promise<EventDto> => {
    const { data } = await apiClient.post<EventDto>('/api/v1/admin/events', request);
    return data;
  },
  /** PUT /api/v1/admin/events/{id}. */
  update: async (id: number, request: UpsertEventRequest): Promise<EventDto> => {
    const { data } = await apiClient.put<EventDto>(`/api/v1/admin/events/${id}`, request);
    return data;
  },
  /** DELETE /api/v1/admin/events/{id}. */
  remove: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/v1/admin/events/${id}`);
  },
  /** POST /api/v1/admin/events/bulk-upload — create events from a .csv/.xlsx. */
  bulkUpload: async (file: File): Promise<BulkUploadResult> => {
    const form = new FormData();
    form.append('file', file);
    const { data } = await apiClient.post<BulkUploadResult>('/api/v1/admin/events/bulk-upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },
};
