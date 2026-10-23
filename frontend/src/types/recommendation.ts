// FOUNDATION-owned recommendation types (mirror backend CatalogRecommendation / RecommendationDto).
// The engine is polymorphic: source/target items are referenced by (itemType, itemId). Public reads
// return resolved CatalogItemDto[]; the admin view carries resolved source/target names for the grid.

export type RecommendationType = 'UPSELL' | 'CROSS_SELL';

export interface RecommendationDto {
  id: number;
  sourceItemType: string;
  sourceItemId: number;
  sourceItemName: string | null;
  targetItemType: string;
  targetItemId: number;
  targetItemName: string | null;
  relationType: RecommendationType;
  score: number;
  active: boolean;
}

export interface UpsertRecommendationRequest {
  sourceItemType: string;
  sourceItemId: number;
  targetItemType: string;
  targetItemId: number;
  relationType: RecommendationType;
  score?: number;
  active?: boolean;
}
