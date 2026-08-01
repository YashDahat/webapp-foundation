// GENERATED cart spine — canonical commerce provider (cart state only, headless), do not edit.
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { CartItem, CartTotals } from './types';
import { computeTotals } from './pricing';
import { cartStorage, pricingRules } from './cartConfig';

export interface CartContextValue {
  cartItems: CartItem[];
  addItem: (item: Omit<CartItem, 'quantity'>, quantity?: number) => void;
  removeItem: (id: CartItem['id'], variantKey?: string) => void;
  setItemQuantity: (id: CartItem['id'], quantity: number, variantKey?: string) => void;
  clearCart: () => void;
  totals: CartTotals;
  cartCount: number;
}

const lineKey = (id: CartItem['id'], variantKey?: string): string => `${id}::${variantKey ?? ''}`;

const CartContext = createContext<CartContextValue | undefined>(undefined);

export const CartProvider = ({ children }: { children: ReactNode }) => {
  const [cartItems, setCartItems] = useState<CartItem[]>(() => cartStorage.load());

  useEffect(() => {
    cartStorage.save(cartItems);
  }, [cartItems]);

  const addItem = (item: Omit<CartItem, 'quantity'>, quantity: number = 1) => {
    setCartItems((prev) => {
      const key = lineKey(item.id, item.variantKey);
      const existing = prev.find((i) => lineKey(i.id, i.variantKey) === key);
      if (existing) {
        return prev.map((i) =>
          lineKey(i.id, i.variantKey) === key ? { ...i, quantity: i.quantity + quantity } : i,
        );
      }
      return [...prev, { ...item, quantity }];
    });
  };

  const removeItem = (id: CartItem['id'], variantKey?: string) =>
    setCartItems((prev) => prev.filter((i) => lineKey(i.id, i.variantKey) !== lineKey(id, variantKey)));

  const setItemQuantity = (id: CartItem['id'], quantity: number, variantKey?: string) =>
    setCartItems((prev) =>
      quantity <= 0
        ? prev.filter((i) => lineKey(i.id, i.variantKey) !== lineKey(id, variantKey))
        : prev.map((i) =>
            lineKey(i.id, i.variantKey) === lineKey(id, variantKey) ? { ...i, quantity } : i,
          ),
    );

  const clearCart = () => setCartItems([]);

  const totals = useMemo(() => computeTotals(cartItems, pricingRules), [cartItems]);
  const cartCount = useMemo(() => cartItems.reduce((n, i) => n + i.quantity, 0), [cartItems]);

  return (
    <CartContext.Provider
      value={{ cartItems, addItem, removeItem, setItemQuantity, clearCart, totals, cartCount }}
    >
      {children}
    </CartContext.Provider>
  );
};

export const useCart = (): CartContextValue => {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart must be used within a CartProvider');
  return ctx;
};
