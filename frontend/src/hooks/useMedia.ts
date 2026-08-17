// GENERATED foundation scaffold — do not edit by hand.
// React Query hook over the media library SDK (admin). Lists all uploaded images.
import { useQuery } from '@tanstack/react-query';
import { getMediaAssets } from '@/services/mediaService';
import type { MediaAssetDto } from '@/types/media';

export function useMedia() {
  return useQuery<MediaAssetDto[]>({
    queryKey: ['media'],
    queryFn: getMediaAssets,
  });
}
