// FOUNDATION-owned recommendation SDK (com.webappfoundation RecommendationController +
// AdminRecommendationController). Public reads are unauthenticated; admin endpoints are ADMIN-gated.
import apiClient from '@/api/client';
import type { BulkUploadResult } from '@/types/bulkUpload';
import type { CatalogItemDto } from '@/types/catalog';
import type { RecommendationDto, RecommendationType, UpsertRecommendationRequest } from '@/types/recommendation';

export const recommendationService = {
  // ── Public ──
  /** GET /api/v1/recommendations — resolved items (any sellable type), highest score first. */
  get: async (
    itemType: string,
    itemId: number,
    type: RecommendationType,
    branchId?: number,
  ): Promise<CatalogItemDto[]> => {
    const { data } = await apiClient.get<CatalogItemDto[]>('/api/v1/recommendations', {
      params: { itemType, itemId, type, branchId: branchId ?? undefined },
    });
    return data;
  },

  // ── Admin ──
  /** GET /api/v1/admin/recommendations — all rows with resolved source/target names. */
  adminList: async (): Promise<RecommendationDto[]> => {
    const { data } = await apiClient.get<RecommendationDto[]>('/api/v1/admin/recommendations');
    return data;
  },
  /** POST /api/v1/admin/recommendations. */
  create: async (request: UpsertRecommendationRequest): Promise<RecommendationDto> => {
    const { data } = await apiClient.post<RecommendationDto>('/api/v1/admin/recommendations', request);
    return data;
  },
  /** PUT /api/v1/admin/recommendations/{id}. */
  update: async (id: number, request: UpsertRecommendationRequest): Promise<RecommendationDto> => {
    const { data } = await apiClient.put<RecommendationDto>(`/api/v1/admin/recommendations/${id}`, request);
    return data;
  },
  /** DELETE /api/v1/admin/recommendations/{id}. */
  remove: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/v1/admin/recommendations/${id}`);
  },
  /** POST /api/v1/admin/recommendations/bulk-upload. */
  bulkUpload: async (file: File): Promise<BulkUploadResult> => {
    const form = new FormData();
    form.append('file', file);
    const { data } = await apiClient.post<BulkUploadResult>('/api/v1/admin/recommendations/bulk-upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },
};
