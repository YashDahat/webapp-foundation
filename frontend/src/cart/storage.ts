// GENERATED cart spine — persistence strategies, do not edit.
import type { CartItem, CartStorage } from './types';

export const localStorageCart = (key: string = 'cart'): CartStorage => ({
  load: () => {
    try {
      const raw = localStorage.getItem(key);
      return raw ? (JSON.parse(raw) as CartItem[]) : [];
    } catch {
      return [];
    }
  },
  save: (items) => {
    try {
      localStorage.setItem(key, JSON.stringify(items));
    } catch {
      /* storage unavailable — cart stays in memory for this session */
    }
  },
});

export const memoryCart = (): CartStorage => {
  let state: CartItem[] = [];
  return {
    load: () => state,
    save: (items) => {
      state = items;
    },
  };
};
