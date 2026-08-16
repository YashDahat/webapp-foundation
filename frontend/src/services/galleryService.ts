// GENERATED from the backend API contract — do not edit by hand.
// Typed client for the FOUNDATION-owned gallery API — mirrors com.webappfoundation gallery
// controllers (ApiInventory skips them, so the worker never derives this file). Public reads +
// admin writes; the current user/authorization is handled by the shared apiClient + backend.
import apiClient from '@/api/client';
import type { GalleryItemDto, GallerySection, GalleryUpdateRequest } from '@/types/gallery';

/** GET /api/v1/gallery — public list, optionally filtered by section. */
export const getGallery = async (section?: GallerySection): Promise<GalleryItemDto[]> => {
  const response = await apiClient.get<GalleryItemDto[]>('/api/v1/gallery', {
    params: section ? { section } : undefined,
  });
  return response.data;
};

/** POST /api/v1/admin/gallery — ADMIN multipart upload (image file + metadata). */
export const uploadGalleryItem = async (input: {
  file: File;
  caption?: string;
  section?: GallerySection;
  eventName?: string;
  eventDate?: string;
}): Promise<GalleryItemDto> => {
  const form = new FormData();
  form.append('file', input.file);
  if (input.caption) form.append('caption', input.caption);
  if (input.section) form.append('section', input.section);
  if (input.eventName) form.append('eventName', input.eventName);
  if (input.eventDate) form.append('eventDate', input.eventDate);
  const response = await apiClient.post<GalleryItemDto>('/api/v1/admin/gallery', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
};

/** PUT /api/v1/admin/gallery/{id} — ADMIN edit metadata. */
export const updateGalleryItem = async (
  id: number,
  request: GalleryUpdateRequest,
): Promise<GalleryItemDto> => {
  const response = await apiClient.put<GalleryItemDto>(`/api/v1/admin/gallery/${id}`, request);
  return response.data;
};

/** DELETE /api/v1/admin/gallery/{id} — ADMIN delete. */
export const deleteGalleryItem = async (id: number): Promise<void> => {
  await apiClient.delete<void>(`/api/v1/admin/gallery/${id}`);
};
