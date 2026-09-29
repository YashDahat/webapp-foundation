// FOUNDATION-owned support SDK (com.webappfoundation SupportController + AdminSupportController).
// Customer endpoints require a logged-in user; admin endpoints are ADMIN-gated.
import apiClient from '@/api/client';
import type { MediaAssetDto } from '@/types/media';
import type {
  AdminReplyRequest,
  CreateSupportCaseRequest,
  PostMessageRequest,
  SupportCaseDto,
  SupportThreadDto,
} from '@/types/support';

export const supportService = {
  /**
   * POST /api/v1/support/attachments — upload an image attachment (any logged-in user, customers
   * included). Returns the stored media asset; use its `url` in the message's `attachments`.
   */
  uploadAttachment: async (file: File): Promise<MediaAssetDto> => {
    const form = new FormData();
    form.append('file', file);
    const { data } = await apiClient.post<MediaAssetDto>('/api/v1/support/attachments', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },

  // ── Customer ──
  /** POST /api/v1/support — open a new case (subject + first message). */
  open: async (request: CreateSupportCaseRequest): Promise<SupportCaseDto> => {
    const { data } = await apiClient.post<SupportCaseDto>('/api/v1/support', request);
    return data;
  },
  /** GET /api/v1/support — the current customer's cases. */
  myCases: async (): Promise<SupportCaseDto[]> => {
    const { data } = await apiClient.get<SupportCaseDto[]>('/api/v1/support');
    return data;
  },
  /** GET /api/v1/support/{id} — the current customer's own case thread. */
  thread: async (id: number): Promise<SupportThreadDto> => {
    const { data } = await apiClient.get<SupportThreadDto>(`/api/v1/support/${id}`);
    return data;
  },
  /** POST /api/v1/support/{id}/messages — customer adds a message. */
  reply: async (id: number, request: PostMessageRequest): Promise<SupportCaseDto> => {
    const { data } = await apiClient.post<SupportCaseDto>(`/api/v1/support/${id}/messages`, request);
    return data;
  },

  // ── Admin helpdesk ──
  /** GET /api/v1/admin/support — all cases. */
  all: async (): Promise<SupportCaseDto[]> => {
    const { data } = await apiClient.get<SupportCaseDto[]>('/api/v1/admin/support');
    return data;
  },
  /** GET /api/v1/admin/support/{id} — any case's thread. */
  adminThread: async (id: number): Promise<SupportThreadDto> => {
    const { data } = await apiClient.get<SupportThreadDto>(`/api/v1/admin/support/${id}`);
    return data;
  },
  /** POST /api/v1/admin/support/{id}/reply — staff reply (+ optional status change); emails the customer. */
  adminReply: async (id: number, request: AdminReplyRequest): Promise<SupportThreadDto> => {
    const { data } = await apiClient.post<SupportThreadDto>(`/api/v1/admin/support/${id}/reply`, request);
    return data;
  },
};
