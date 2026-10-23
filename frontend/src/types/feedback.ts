// FOUNDATION-owned feedback types (mirror backend OrderFeedbackDto / CreateFeedbackRequest).
// A tiny post-checkout rating, one per order.

export interface OrderFeedbackDto {
  id: number;
  orderId: number;
  orderNumber?: string | null;
  rating: number;
  comment?: string | null;
  createdAt: string;
}

export interface CreateFeedbackRequest {
  rating: number;
  comment?: string | null;
}
