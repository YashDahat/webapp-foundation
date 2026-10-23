// FOUNDATION-owned product-review SDK (com.webappfoundation ProductReviewController +
// AdminReviewController). Reading the summary is public; submit/delete require a signed-in user; admin
// endpoints are ADMIN-gated.
import apiClient from '@/api/client';
import type {
  CreateProductReviewRequest, ProductReviewDto, ProductReviewSummaryDto,
} from '@/types/review';

export const reviewService = {
  // ── Public / auth ──
  /** GET /api/v1/products/{productId}/reviews — aggregate + list (+ the caller's own review if signed in). */
  summary: async (productId: number): Promise<ProductReviewSummaryDto> => {
    const { data } = await apiClient.get<ProductReviewSummaryDto>(`/api/v1/products/${productId}/reviews`);
    return data;
  },
  /** POST /api/v1/products/{productId}/reviews — submit/update the caller's review (auth). */
  submit: async (productId: number, request: CreateProductReviewRequest): Promise<ProductReviewDto> => {
    const { data } = await apiClient.post<ProductReviewDto>(`/api/v1/products/${productId}/reviews`, request);
    return data;
  },
  /** DELETE /api/v1/products/{productId}/reviews/mine — remove the caller's own review (auth). */
  removeMine: async (productId: number): Promise<void> => {
    await apiClient.delete(`/api/v1/products/${productId}/reviews/mine`);
  },

  // ── Admin ──
  /** GET /api/v1/admin/reviews — all reviews (newest first) with resolved product names. */
  adminList: async (): Promise<ProductReviewDto[]> => {
    const { data } = await apiClient.get<ProductReviewDto[]>('/api/v1/admin/reviews');
    return data;
  },
  /** DELETE /api/v1/admin/reviews/{id} — moderate (delete) a review. */
  adminRemove: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/v1/admin/reviews/${id}`);
  },
};
