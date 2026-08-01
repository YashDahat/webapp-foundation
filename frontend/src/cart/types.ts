// GENERATED cart spine — canonical commerce contracts, do not edit.

/**
 * A single line in the cart. Generic across verticals: a dish, a drink, a gym scheme, a
 * ticket, or a wholesale product all map to this shape. `id` is the product/service id;
 * `variantKey` distinguishes variants of the same id (size, add-ons, time-slot); `metadata`
 * carries any domain-specific data and never affects pricing or merging.
 */
export interface CartItem {
  id: string | number;
  name: string;
  unitPrice: number;
  quantity: number;
  imageUrl?: string | null;
  variantKey?: string;
  metadata?: Record<string, unknown>;
}

export interface PricingContext {
  items: CartItem[];
  subtotal: number;
  runningTotal: number;
}

/** A single, composable price adjustment. Returns a signed amount: + for fees/tax, - for discounts. */
export interface PricingRule {
  id: string;
  label: string;
  apply: (ctx: PricingContext) => number;
}

export interface AdjustmentLine {
  id: string;
  label: string;
  amount: number;
}

export interface CartTotals {
  subtotal: number;
  adjustments: AdjustmentLine[];
  total: number;
}

/** Persistence strategy — swap localStorage for a session or backend-synced implementation. */
export interface CartStorage {
  load: () => CartItem[];
  save: (items: CartItem[]) => void;
}

/** One checkout step. `validate` gates advancing: return true, or an error message to block. */
export interface CheckoutStep {
  id: string;
  label: string;
  validate?: () => boolean | string;
}
