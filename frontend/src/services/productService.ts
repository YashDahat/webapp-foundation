// FOUNDATION-owned product SDK (com.webappfoundation ProductController + AdminProductController).
// Public reads are unauthenticated; admin endpoints are ADMIN-gated.
import apiClient from '@/api/client';
import type { BulkUploadResult } from '@/types/bulkUpload';
import type { ProductDto, ProductFilter, UpsertProductRequest } from '@/types/product';

export const productService = {
  // ── Public ──
  /** GET /api/v1/products — active products, optionally filtered by category, search, and branch. */
  list: async (filter: ProductFilter = {}): Promise<ProductDto[]> => {
    const { data } = await apiClient.get<ProductDto[]>('/api/v1/products', {
      params: {
        category: filter.category || undefined,
        q: filter.q || undefined,
        branchId: filter.branchId ?? undefined,
      },
    });
    return data;
  },
  /** GET /api/v1/products/{id}. */
  get: async (id: number): Promise<ProductDto> => {
    const { data } = await apiClient.get<ProductDto>(`/api/v1/products/${id}`);
    return data;
  },
  /** GET /api/v1/products/slug/{slug}. */
  getBySlug: async (slug: string): Promise<ProductDto> => {
    const { data } = await apiClient.get<ProductDto>(`/api/v1/products/slug/${slug}`);
    return data;
  },

  // ── Admin ──
  /** GET /api/v1/admin/products — all products incl. inactive. */
  adminList: async (): Promise<ProductDto[]> => {
    const { data } = await apiClient.get<ProductDto[]>('/api/v1/admin/products');
    return data;
  },
  /** POST /api/v1/admin/products. */
  create: async (request: UpsertProductRequest): Promise<ProductDto> => {
    const { data } = await apiClient.post<ProductDto>('/api/v1/admin/products', request);
    return data;
  },
  /** PUT /api/v1/admin/products/{id}. */
  update: async (id: number, request: UpsertProductRequest): Promise<ProductDto> => {
    const { data } = await apiClient.put<ProductDto>(`/api/v1/admin/products/${id}`, request);
    return data;
  },
  /** DELETE /api/v1/admin/products/{id}. */
  remove: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/v1/admin/products/${id}`);
  },
  /** POST /api/v1/admin/products/bulk-upload — create products from a .csv/.xlsx. */
  bulkUpload: async (file: File): Promise<BulkUploadResult> => {
    const form = new FormData();
    form.append('file', file);
    const { data } = await apiClient.post<BulkUploadResult>('/api/v1/admin/products/bulk-upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },
};
