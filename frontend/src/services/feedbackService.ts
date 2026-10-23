// FOUNDATION-owned feedback SDK (com.webappfoundation OrderController feedback + AdminFeedbackController).
import apiClient from '@/api/client';
import type { CreateFeedbackRequest, OrderFeedbackDto } from '@/types/feedback';

export const feedbackService = {
  /** POST /api/v1/orders/{id}/feedback — submit a rating (submit-once, ownership-checked). */
  submit: async (orderId: number, request: CreateFeedbackRequest): Promise<OrderFeedbackDto> => {
    const { data } = await apiClient.post<OrderFeedbackDto>(`/api/v1/orders/${orderId}/feedback`, request);
    return data;
  },
  /** GET /api/v1/admin/feedback — all submitted feedback (read-only). */
  adminList: async (): Promise<OrderFeedbackDto[]> => {
    const { data } = await apiClient.get<OrderFeedbackDto[]>('/api/v1/admin/feedback');
    return data;
  },
};
