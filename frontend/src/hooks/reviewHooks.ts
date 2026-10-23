// React Query hooks over the review SDK. Mutations wrap withToast for success; failures auto-toast via the
// apiClient interceptor (no manual toast.error).
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { reviewService } from '@/services/reviewService';
import { withToast } from '@/lib/apiToast';
import type { CreateProductReviewRequest, ProductReviewDto, ProductReviewSummaryDto } from '@/types/review';

// ── Public / auth ──
export function useProductReviews(productId: number | null) {
  return useQuery<ProductReviewSummaryDto>({
    queryKey: ['product-reviews', productId],
    queryFn: () => reviewService.summary(productId as number),
    enabled: productId != null,
  });
}

export function useSubmitReview(productId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateProductReviewRequest) =>
      withToast(reviewService.submit(productId, req), 'Thanks for your review'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['product-reviews', productId] }),
  });
}

export function useDeleteMyReview(productId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => withToast(reviewService.removeMine(productId), 'Review removed'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['product-reviews', productId] }),
  });
}

// ── Admin ──
export function useAdminReviews() {
  return useQuery<ProductReviewDto[]>({ queryKey: ['admin-reviews'], queryFn: reviewService.adminList });
}

export function useAdminDeleteReview() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => withToast(reviewService.adminRemove(id), 'Review deleted'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-reviews'] }),
  });
}
