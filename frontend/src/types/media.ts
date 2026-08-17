// GENERATED from the backend API contract — do not edit by hand.
// Source of truth: webapp-foundation backend (com.webappfoundation media library). FOUNDATION-OWNED:
// ApiInventory skips AdminMediaController, so the worker never derives these.
import type { GallerySection } from '@/types/gallery';

/** A media-library entry — com.webappfoundation.dto.MediaAssetDto. `url` is ready for <img src> or to
 *  store on a domain entity (e.g. a Trainer's photoUrl). An asset can optionally be published to the
 *  public gallery (showInGallery + section/event). */
export interface MediaAssetDto {
  id: number;
  url: string;
  filename: string | null;
  contentType: string;
  sizeBytes: number | null;
  label: string | null;
  showInGallery: boolean;
  section: GallerySection | null;
  eventName: string | null;
  eventDate: string | null;
  sortOrder: number | null;
  uploadedAt: string; // ISO instant
}
