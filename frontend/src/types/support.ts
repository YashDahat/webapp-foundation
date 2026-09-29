// FOUNDATION-owned support types (com.webappfoundation support). Threaded tickets: a customer opens a
// case and both sides exchange messages; each staff reply emails the customer.

export type SupportStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED';
export type SupportAuthor = 'CUSTOMER' | 'STAFF';

export interface SupportCaseDto {
  id: number;
  userId: number;
  customerName: string | null;
  customerEmail: string | null;
  subject: string;
  status: SupportStatus;
  createdAt: string; // ISO instant
  updatedAt: string; // ISO instant
}

export interface SupportMessageDto {
  id: number;
  caseId: number;
  author: SupportAuthor;
  body: string;
  /** Image attachment URLs (media-library /api/v1/media/<key>), in order. */
  attachments: string[];
  createdAt: string; // ISO instant
}

export interface SupportThreadDto {
  supportCase: SupportCaseDto;
  messages: SupportMessageDto[];
}

export interface CreateSupportCaseRequest {
  subject: string;
  message: string;
  attachments?: string[];
}

export interface PostMessageRequest {
  body: string;
  attachments?: string[];
}

export interface AdminReplyRequest {
  body: string;
  status?: SupportStatus;
  attachments?: string[];
}
