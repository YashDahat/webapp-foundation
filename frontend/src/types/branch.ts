// FOUNDATION-owned branch types (mirror backend BranchDto / UpsertBranchRequest). A physical outlet an
// order is fulfilled from; the selected branch filters the catalog + recommendations and is recorded on orders.

export interface BranchDto {
  id: number;
  name: string;
  slug: string;
  phone?: string | null;
  addressLine?: string | null;
  city?: string | null;
  state?: string | null;
  pincode?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  /** A shareable Google Maps link for the outlet. */
  googleMapsUrl?: string | null;
  openingHours?: string | null;
  active: boolean;
  /** Set only when queried via /nearest. */
  distanceKm?: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface UpsertBranchRequest {
  name: string;
  slug?: string | null;
  phone?: string | null;
  addressLine?: string | null;
  city?: string | null;
  state?: string | null;
  pincode?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  googleMapsUrl?: string | null;
  openingHours?: string | null;
  active?: boolean;
}
