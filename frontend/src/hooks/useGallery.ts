// GENERATED foundation scaffold — do not edit by hand.
// React Query hook over the foundation gallery SDK. Use this to read gallery items in any page or
// component; pass a section to filter (WEBSITE for the main gallery, EVENT for event photos).
import { useQuery } from '@tanstack/react-query';
import { getGallery } from '@/services/galleryService';
import type { GalleryItemDto, GallerySection } from '@/types/gallery';

export function useGallery(section?: GallerySection) {
  return useQuery<GalleryItemDto[]>({
    queryKey: ['gallery', section ?? 'ALL'],
    queryFn: () => getGallery(section),
  });
}
