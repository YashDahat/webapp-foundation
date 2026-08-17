// GENERATED from the backend API contract — do not edit by hand.
// Public read-only gallery SDK (com.webappfoundation GalleryController). The gallery is a view over the
// media library — admins publish images to it from /admin/media (see @/services/mediaService).
import apiClient from '@/api/client';
import type { GalleryItemDto, GallerySection } from '@/types/gallery';

/** GET /api/v1/gallery — public list, optionally filtered by section. */
export const getGallery = async (section?: GallerySection): Promise<GalleryItemDto[]> => {
  const response = await apiClient.get<GalleryItemDto[]>('/api/v1/gallery', {
    params: section ? { section } : undefined,
  });
  return response.data;
};
