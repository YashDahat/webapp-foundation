// React Query hooks over the recommendation SDK. Public reads are enabled only once itemId is known;
// mutations wrap withToast (failures auto-toast via the apiClient interceptor — no manual toast.error).
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { recommendationService } from '@/services/recommendationService';
import { withToast } from '@/lib/apiToast';
import type { CatalogItemDto } from '@/types/catalog';
import type { RecommendationDto, RecommendationType, UpsertRecommendationRequest } from '@/types/recommendation';

// ── Public ──
export function useRecommendations(
  itemType: string,
  itemId: number | null,
  type: RecommendationType,
  branchId?: number,
) {
  return useQuery<CatalogItemDto[]>({
    queryKey: ['recommendations', itemType, itemId, type, branchId ?? null],
    queryFn: () => recommendationService.get(itemType, itemId as number, type, branchId),
    enabled: itemId != null,
  });
}

// ── Admin ──
export function useAdminRecommendations() {
  return useQuery<RecommendationDto[]>({
    queryKey: ['admin-recommendations'],
    queryFn: recommendationService.adminList,
  });
}

export function useCreateRecommendation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: UpsertRecommendationRequest) =>
      withToast(recommendationService.create(req), 'Recommendation created'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-recommendations'] }),
  });
}

export function useUpdateRecommendation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: number; req: UpsertRecommendationRequest }) =>
      withToast(recommendationService.update(id, req), 'Recommendation updated'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-recommendations'] }),
  });
}

export function useDeleteRecommendation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => withToast(recommendationService.remove(id), 'Recommendation deleted'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-recommendations'] }),
  });
}
