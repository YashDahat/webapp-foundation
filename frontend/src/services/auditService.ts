// FOUNDATION-owned audit-log SDK (com.webappfoundation AdminAuditController). ADMIN-gated, read-only.
import apiClient from '@/api/client';
import type { AuditEventDto } from '@/types/audit';

export interface AuditQuery {
  action?: string;
  actorEmail?: string;
  limit?: number;
}

export const auditService = {
  /** GET /api/v1/admin/audit — latest audit events (newest first), optionally filtered. */
  list: async (query: AuditQuery = {}): Promise<AuditEventDto[]> => {
    const { data } = await apiClient.get<AuditEventDto[]>('/api/v1/admin/audit', {
      params: {
        action: query.action || undefined,
        actorEmail: query.actorEmail || undefined,
        limit: query.limit ?? 200,
      },
    });
    return data;
  },
};
