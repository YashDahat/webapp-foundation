// FOUNDATION-owned catalog contract (mirrors backend CatalogItemDto). The universal "sellable" shape
// shared by the cart, recommendations, and orders. Any sellable type (Product, Room, Membership, …)
// maps to this via its backend CatalogItemResolver, referenced polymorphically by (itemType, itemId).

export interface CatalogItemDto {
  itemType: string; // e.g. 'PRODUCT', 'ROOM', 'MEMBERSHIP'
  itemId: number;
  name: string;
  description?: string | null;
  price: number;
  priceUnit?: string | null; // e.g. 'per night', 'per session'
  imageUrl?: string | null;
  images?: string[] | null;
  slug?: string | null;
  detailPath?: string | null; // front-end route to the item's detail page, e.g. '/products/<slug>'
  category?: string | null;
  active: boolean;
  cartable: boolean; // one-click Add (true) vs. needs configuration first (false)
  workflowCode?: string | null;
  attributes?: Record<string, unknown> | null;
}
