// FOUNDATION-owned product types (mirror backend ProductDto / UpsertProductRequest). The default
// batteries-included sellable. Stock is opt-in (stockTracked); variants make the product non-cartable
// (pick a size on the detail page); branch availability + per-branch stock are set on the admin form.

export type BranchScope = 'GLOBAL' | 'SELECTED';

export interface ProductVariantDto {
  id: number;
  label: string;
  sku?: string | null;
  price?: number | null; // null → inherit the product price
  stockTracked: boolean;
  stock?: number | null;
  reserved?: number | null;
  available?: number | null; // computed (shared pool); null when untracked or per-branch
}

export interface ProductDto {
  id: number;
  name: string;
  slug: string;
  description?: string | null;
  price: number;
  branchScope: BranchScope;
  availableBranchIds?: number[] | null;
  stockTracked: boolean;
  stockPerBranch: boolean;
  stock?: number | null;
  reserved?: number | null;
  available?: number | null; // computed (shared pool); null when unlimited or per-branch
  variants: ProductVariantDto[];
  imageUrl?: string | null;
  images?: string[] | null;
  features?: string[] | null; // short highlight bullets shown on the detail page
  category?: string | null;
  active: boolean;
  workflowCode?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface VariantInput {
  id?: number | null; // present when editing an existing variant
  label: string;
  sku?: string | null;
  price?: number | null;
  stockTracked: boolean;
  stock?: number | null;
}

export interface BranchStockInput {
  variantId?: number | null; // null for a variant-less product
  branchId: number;
  stock?: number | null;
}

export interface UpsertProductRequest {
  name: string;
  slug?: string | null;
  description?: string | null;
  price: number;
  category?: string | null;
  active?: boolean;
  workflowCode?: string | null;
  branchScope?: BranchScope;
  availableBranchIds?: number[] | null;
  stockTracked: boolean;
  stockPerBranch: boolean;
  stock?: number | null;
  imageUrl?: string | null;
  images?: string[] | null;
  features?: string[] | null;
  variants?: VariantInput[];
  branchStock?: BranchStockInput[];
}

/** Query filter for the public catalog listing. */
export interface ProductFilter {
  category?: string;
  q?: string;
  branchId?: number;
}
