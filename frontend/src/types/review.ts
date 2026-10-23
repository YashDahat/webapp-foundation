// FOUNDATION-owned product-review types (mirror backend ProductReviewDto / ProductReviewSummaryDto /
// CreateProductReviewRequest). Any signed-in user may leave ONE review per product (submit is an upsert),
// delete their own, and read the public summary. Admins moderate (delete) at /admin/reviews.

export interface ProductReviewDto {
  id: number;
  productId: number;
  productName?: string | null; // populated only in the admin list
  userId: number;
  userName: string;
  rating: number;
  comment?: string | null;
  createdAt: string;
  mine: boolean; // the current caller's own review
}

export interface ProductReviewSummaryDto {
  average: number;
  count: number;
  distribution: Record<number, number>; // star (1–5) → count
  reviews: ProductReviewDto[];
  myReview?: ProductReviewDto | null;
}

export interface CreateProductReviewRequest {
  rating: number; // 1–5
  comment?: string | null;
}
