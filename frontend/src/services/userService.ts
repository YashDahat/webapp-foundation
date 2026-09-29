// FOUNDATION-owned admin user-management SDK (com.webappfoundation AdminUserController). ADMIN-gated;
// deleting a user is SUPER_ADMIN-only (enforced server-side).
import apiClient from '@/api/client';
import type { BulkUploadResult } from '@/types/bulkUpload';
import type { Role } from '@/types/auth';
import type { UserDto } from '@/types/user';

export const userService = {
  /** GET /api/v1/admin/users — all users. */
  list: async (): Promise<UserDto[]> => {
    const { data } = await apiClient.get<UserDto[]>('/api/v1/admin/users');
    return data;
  },
  /** PUT /api/v1/admin/users/{id}/role — change a user's role (SUPER_ADMIN is single-holder). */
  changeRole: async (id: number, role: Role): Promise<UserDto> => {
    const { data } = await apiClient.put<UserDto>(`/api/v1/admin/users/${id}/role`, { role });
    return data;
  },
  /** DELETE /api/v1/admin/users/{id} — SUPER_ADMIN only. */
  remove: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/v1/admin/users/${id}`);
  },
  /** POST /api/v1/admin/users/bulk-upload — create users from a .csv/.xlsx. */
  bulkUpload: async (file: File): Promise<BulkUploadResult> => {
    const form = new FormData();
    form.append('file', file);
    const { data } = await apiClient.post<BulkUploadResult>('/api/v1/admin/users/bulk-upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data;
  },
};
