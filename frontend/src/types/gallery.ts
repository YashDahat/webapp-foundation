// GENERATED from the backend API contract — do not edit by hand.
// Source of truth: webapp-foundation backend (com.webappfoundation gallery spine). Gallery is
// FOUNDATION-OWNED: ApiInventory skips its controllers, so the worker never auto-derives these —
// they live here so the frontend has real, importable types. ApiContractCard picks this file up
// via the marker line above and feeds it to the LLM.

/** Where an item belongs — com.webappfoundation.model.GallerySection. */
export type GallerySection = 'WEBSITE' | 'EVENT';

/** A gallery item — com.webappfoundation.dto.GalleryItemDto. `url` is served by MediaController. */
export interface GalleryItemDto {
  id: number;
  url: string;
  caption: string | null;
  section: GallerySection;
  eventName: string | null;
  eventDate: string | null; // ISO date
  sortOrder: number;
}

/** Editable metadata for PUT /api/v1/admin/gallery/{id} — com.webappfoundation.dto.GalleryUpdateRequest. */
export interface GalleryUpdateRequest {
  caption?: string | null;
  section?: GallerySection;
  eventName?: string | null;
  eventDate?: string | null;
  sortOrder?: number;
}
