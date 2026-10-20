// FOUNDATION-owned audit-log types (com.webappfoundation audit). Read-only admin view of user activity.

export interface AuditEventDto {
  id: number;
  createdAt: string; // ISO instant
  userId: number | null;
  actorEmail: string | null;
  actorRole: string | null;
  action: string;
  outcome: string | null;
  targetType: string | null;
  targetId: string | null;
  ipAddress: string | null;
  requestId: string | null;
  detail: string | null;
}
