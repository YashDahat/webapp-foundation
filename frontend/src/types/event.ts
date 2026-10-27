// FOUNDATION-owned event types (mirror backend EventDto / UpsertEventRequest). A dated, finite-capacity
// sellable (itemType="EVENT"). Seats are capacity-tracked (shared pool, or per tier); tiered events are
// non-cartable (pick a tier on the detail page). Instants are ISO-8601 strings.

export interface EventTierDto {
  id: number;
  label: string;
  sku?: string | null;
  price?: number | null; // null → inherit the event's base price
  stock?: number | null;
  reserved?: number | null;
  available?: number | null; // computed; null when the tier is unlimited
}

export interface EventDto {
  id: number;
  name: string;
  slug: string;
  description?: string | null;
  price: number;
  startsAt: string;
  endsAt?: string | null;
  salesCloseAt?: string | null;
  venueBranchId?: number | null;
  venueName?: string | null;
  capacity?: number | null;
  reserved?: number | null;
  available?: number | null; // seats left on the shared pool; null when unlimited or tiered
  seatsLeft?: number | null; // remaining seats across the event; null when unlimited
  onSale: boolean;
  soldOut: boolean;
  tiers: EventTierDto[];
  imageUrl?: string | null;
  images?: string[] | null;
  features?: string[] | null;
  category?: string | null;
  active: boolean;
  workflowCode?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface EventFilter {
  category?: string;
  q?: string;
  branchId?: number;
}

export interface TierInput {
  id?: number | null; // present when editing an existing tier
  label: string;
  sku?: string | null;
  price?: number | null;
  stock?: number | null;
}

export interface UpsertEventRequest {
  name: string;
  slug?: string | null;
  description?: string | null;
  price: number;
  category?: string | null;
  active?: boolean;
  workflowCode?: string | null;
  startsAt: string;
  endsAt?: string | null;
  salesCloseAt?: string | null;
  venueBranchId?: number | null;
  capacity?: number | null;
  imageUrl?: string | null;
  images?: string[] | null;
  features?: string[] | null;
  tiers?: TierInput[];
}
