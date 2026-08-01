// GENERATED cart spine — pricing engine + rule factories, do not edit.
import type { AdjustmentLine, CartItem, CartTotals, PricingRule } from './types';

const round2 = (n: number): number => Math.round(n * 100) / 100;

/**
 * Applies an ordered list of pricing rules to the subtotal. Adding tax, delivery or a
 * discount is a new rule in the list — this engine never changes (Open/Closed).
 */
export function computeTotals(items: CartItem[], rules: PricingRule[]): CartTotals {
  const subtotal = round2(items.reduce((sum, i) => sum + i.unitPrice * i.quantity, 0));
  const adjustments: AdjustmentLine[] = [];
  let runningTotal = subtotal;
  for (const rule of rules) {
    const amount = round2(rule.apply({ items, subtotal, runningTotal }));
    if (amount !== 0) {
      adjustments.push({ id: rule.id, label: rule.label, amount });
      runningTotal = round2(runningTotal + amount);
    }
  }
  return { subtotal, adjustments, total: Math.max(0, runningTotal) };
}

// ── Rule factories: configure pricing without editing the engine ──────────────
export const percentageFee = (id: string, label: string, rate: number): PricingRule => ({
  id,
  label,
  apply: ({ subtotal }) => subtotal * rate,
});

export const flatFee = (id: string, label: string, amount: number): PricingRule => ({
  id,
  label,
  apply: () => amount,
});

export const percentageDiscount = (id: string, label: string, rate: number): PricingRule => ({
  id,
  label,
  apply: ({ subtotal }) => -(subtotal * rate),
});

/** Wraps a rule so it is waived once the subtotal reaches a threshold (e.g. free delivery over N). */
export const waivedOver = (rule: PricingRule, threshold: number): PricingRule => ({
  id: rule.id,
  label: rule.label,
  apply: (ctx) => (ctx.subtotal >= threshold ? 0 : rule.apply(ctx)),
});
