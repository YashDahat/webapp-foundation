// GENERATED cart spine — public API barrel (import from '@/cart'), do not edit.
// Headless by design: this framework ships state + logic only. Build all cart UI yourself
// (add-to-cart control, quantity control, cart view) using useCart() and useCheckout().
export type {
  CartItem,
  PricingRule,
  PricingContext,
  AdjustmentLine,
  CartTotals,
  CartStorage,
  CheckoutStep,
} from './types';
export { computeTotals, percentageFee, flatFee, percentageDiscount, waivedOver } from './pricing';
export { localStorageCart, memoryCart } from './storage';
export { CartProvider, useCart, type CartContextValue } from './CartContext';
export { useCheckout, type CheckoutController } from './useCheckout';
export { cartStorage, pricingRules } from './cartConfig';
