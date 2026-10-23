// FOUNDATION-owned order SDK (com.webappfoundation OrderController + CheckoutController + AdminOrderController).
// Customer endpoints require a logged-in user (identity is server-derived); admin endpoints are ADMIN-gated.
import apiClient from '@/api/client';
import type { BulkUploadResult } from '@/types/bulkUpload';
import type {
  OrderDto, PlaceOrderRequest, QuoteRequest, QuoteResponse,
} from '@/types/order';

export const orderService = {
  // ── Customer ──
  /** POST /api/v1/orders — place an order (re-priced + reserved server-side). */
  place: async (request: PlaceOrderRequest): Promise<OrderDto> => {
    const { data } = await apiClient.post<OrderDto>('/api/v1/orders', request);
    return data;
  },
  /** POST /api/v1/checkout/quote — priced options for the current cart + fulfilment. */
  quote: async (request: QuoteRequest): Promise<QuoteResponse> => {
    const { data } = await apiClient.post<QuoteResponse>('/api/v1/checkout/quote', request);
    return data;
  },
  /** GET /api/v1/orders — the current customer's orders. */
  myOrders: async (): Promise<OrderDto[]> => {
    const { data } = await apiClient.get<OrderDto[]>('/api/v1/orders');
    return data;
  },
  /** GET /api/v1/orders/{id} — the current customer's own order. */
  get: async (id: number): Promise<OrderDto> => {
    const { data } = await apiClient.get<OrderDto>(`/api/v1/orders/${id}`);
    return data;
  },
  /** POST /api/v1/orders/{id}/cancel — cancel (stage-policy checked). */
  cancel: async (id: number, reason: string): Promise<OrderDto> => {
    const { data } = await apiClient.post<OrderDto>(`/api/v1/orders/${id}/cancel`, { reason });
    return data;
  },

  // ── Admin ──
  adminList: async (): Promise<OrderDto[]> => {
    const { data } = await apiClient.get<OrderDto[]>('/api/v1/admin/orders');
    return data;
  },
  adminGet: async (id: number): Promise<OrderDto> => {
    const { data } = await apiClient.get<OrderDto>(`/api/v1/admin/orders/${id}`);
    return data;
  },
  advanceStage: async (id: number, stageCode: string): Promise<OrderDto> => {
    const { data } = await apiClient.put<OrderDto>(`/api/v1/admin/orders/${id}/stage`, { stageCode });
    return data;
  },
  adminCancel: async (id: number, reason: string): Promise<OrderDto> => {
    const { data } = await apiClient.post<OrderDto>(`/api/v1/admin/orders/${id}/cancel`, { reason });
    return data;
  },
  markPaid: async (id: number): Promise<OrderDto> => {
    const { data } = await apiClient.post<OrderDto>(`/api/v1/admin/orders/${id}/mark-paid`);
    return data;
  },
  bulkUpload: async (file: File): Promise<BulkUploadResult> => {
    const form = new FormData();
    form.append('file', file);
    const { data } = await apiClient.post<BulkUploadResult>('/api/v1/admin/orders/bulk-upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },
};
