// Cart configuration — EDIT THIS FILE to fit the business. The cart mechanism never changes;
// all business-specific behaviour (pricing, persistence) is wired here.
import type { PricingRule } from './types';
import { localStorageCart } from './storage';

// Where the cart is persisted. Swap for memoryCart() or a custom CartStorage.
export const cartStorage = localStorageCart('cart');

// Ordered price adjustments applied after the subtotal. Add/reorder freely — the engine
// in pricing.ts never changes. To enable one, import the factory and add it here, e.g.:
//   import { percentageFee, flatFee, percentageDiscount, waivedOver } from './pricing';
//   export const pricingRules: PricingRule[] = [
//     percentageFee('gst', 'GST (5%)', 0.05),
//     waivedOver(flatFee('delivery', 'Delivery', 40), 500),
//     percentageDiscount('launch', 'Launch offer (10%)', 0.10),
//   ];
export const pricingRules: PricingRule[] = [];
