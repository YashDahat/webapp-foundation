// GENERATED cart spine — configurable multi-step checkout machine, do not edit.
import { useState } from 'react';
import type { CheckoutStep } from './types';

export interface CheckoutController {
  steps: CheckoutStep[];
  current: CheckoutStep | undefined;
  index: number;
  isFirst: boolean;
  isLast: boolean;
  error: string | null;
  progress: number;
  next: () => boolean;
  back: () => void;
  goTo: (id: string) => void;
}

/**
 * Drives an ordered list of checkout steps — pass as many or as few as the business needs
 * (restaurant: delivery → payment → confirm; gym: schedule → member → payment; wholesale:
 * shipping → review → payment → confirm). Steps are data, so adding one never changes this
 * machine. Each step's optional validate() gates advancing.
 */
export function useCheckout(steps: CheckoutStep[]): CheckoutController {
  const [index, setIndex] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const current = steps[index];
  const isFirst = index === 0;
  const isLast = index >= steps.length - 1;

  const next = (): boolean => {
    const result = current?.validate ? current.validate() : true;
    if (result !== true) {
      setError(typeof result === 'string' ? result : 'Please complete this step to continue.');
      return false;
    }
    setError(null);
    if (!isLast) setIndex((i) => i + 1);
    return true;
  };

  const back = (): void => {
    setError(null);
    if (!isFirst) setIndex((i) => i - 1);
  };

  const goTo = (id: string): void => {
    const target = steps.findIndex((s) => s.id === id);
    if (target >= 0) {
      setError(null);
      setIndex(target);
    }
  };

  return {
    steps,
    current,
    index,
    isFirst,
    isLast,
    error,
    progress: steps.length ? (index + 1) / steps.length : 0,
    next,
    back,
    goTo,
  };
}
