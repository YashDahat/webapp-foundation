// FOUNDATION-owned branch SDK (com.webappfoundation BranchController + AdminBranchController).
import apiClient from '@/api/client';
import type { BulkUploadResult } from '@/types/bulkUpload';
import type { BranchDto, UpsertBranchRequest } from '@/types/branch';

export const branchService = {
  // ── Public ──
  list: async (): Promise<BranchDto[]> => {
    const { data } = await apiClient.get<BranchDto[]>('/api/v1/branches');
    return data;
  },
  nearest: async (lat: number, lng: number): Promise<BranchDto[]> => {
    const { data } = await apiClient.get<BranchDto[]>('/api/v1/branches/nearest', { params: { lat, lng } });
    return data;
  },
  getBySlug: async (slug: string): Promise<BranchDto> => {
    const { data } = await apiClient.get<BranchDto>(`/api/v1/branches/slug/${slug}`);
    return data;
  },

  // ── Admin ──
  adminList: async (): Promise<BranchDto[]> => {
    const { data } = await apiClient.get<BranchDto[]>('/api/v1/admin/branches');
    return data;
  },
  create: async (request: UpsertBranchRequest): Promise<BranchDto> => {
    const { data } = await apiClient.post<BranchDto>('/api/v1/admin/branches', request);
    return data;
  },
  update: async (id: number, request: UpsertBranchRequest): Promise<BranchDto> => {
    const { data } = await apiClient.put<BranchDto>(`/api/v1/admin/branches/${id}`, request);
    return data;
  },
  remove: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/v1/admin/branches/${id}`);
  },
  bulkUpload: async (file: File): Promise<BulkUploadResult> => {
    const form = new FormData();
    form.append('file', file);
    const { data } = await apiClient.post<BulkUploadResult>('/api/v1/admin/branches/bulk-upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },
};
