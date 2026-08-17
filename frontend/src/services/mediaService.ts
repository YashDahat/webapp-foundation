// GENERATED from the backend API contract — do not edit by hand.
// Typed client for the FOUNDATION-owned media library (com.webappfoundation AdminMediaController).
// Upload an image once, get a URL, reference it anywhere (e.g. a Trainer's photoUrl).
import apiClient from '@/api/client';
import type { MediaAssetDto } from '@/types/media';
import type { GallerySection } from '@/types/gallery';

/** Gallery-publishing fields shared by upload + update. */
export interface MediaGalleryFields {
  showInGallery?: boolean;
  section?: GallerySection;
  eventName?: string;
  eventDate?: string;
  sortOrder?: number;
}

function appendGalleryFields(form: FormData, g: MediaGalleryFields) {
  if (g.showInGallery !== undefined) form.append('showInGallery', String(g.showInGallery));
  if (g.section) form.append('section', g.section);
  if (g.eventName) form.append('eventName', g.eventName);
  if (g.eventDate) form.append('eventDate', g.eventDate);
  if (g.sortOrder !== undefined) form.append('sortOrder', String(g.sortOrder));
}

/** GET /api/v1/admin/media — ADMIN list of all media assets. */
export const getMediaAssets = async (): Promise<MediaAssetDto[]> => {
  const response = await apiClient.get<MediaAssetDto[]>('/api/v1/admin/media');
  return response.data;
};

/** POST /api/v1/admin/media — ADMIN upload an image; returns the asset (incl. url). */
export const uploadMedia = async (
  input: { file: File; label?: string } & MediaGalleryFields,
): Promise<MediaAssetDto> => {
  const form = new FormData();
  form.append('file', input.file);
  if (input.label) form.append('label', input.label);
  appendGalleryFields(form, input);
  const response = await apiClient.post<MediaAssetDto>('/api/v1/admin/media', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
};

/** PUT /api/v1/admin/media/{id} — ADMIN replace the image (optional) and/or edit metadata + publishing. */
export const updateMedia = async (
  id: number,
  input: { file?: File; label?: string } & MediaGalleryFields,
): Promise<MediaAssetDto> => {
  const form = new FormData();
  if (input.file) form.append('file', input.file);
  if (input.label !== undefined) form.append('label', input.label);
  appendGalleryFields(form, input);
  const response = await apiClient.put<MediaAssetDto>(`/api/v1/admin/media/${id}`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
};

/** DELETE /api/v1/admin/media/{id} — ADMIN delete. */
export const deleteMedia = async (id: number): Promise<void> => {
  await apiClient.delete<void>(`/api/v1/admin/media/${id}`);
};
