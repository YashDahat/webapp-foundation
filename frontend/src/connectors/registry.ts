// Client-side checkout-connector seam (mirrors the backend connector SPI for the UI layer only). Register
// a connector here to contribute a custom checkout step for its quote options; the default CheckoutPage
// renders a generic option picker, so most connectors need no registration. Backend stage actions are
// server-only — the frontend just displays the resulting stage/timeline.
import type { ComponentType } from 'react';
import type { ConnectorCategory, QuoteOption } from '@/types/order';

export interface CheckoutStepProps {
  options: QuoteOption[];
  selectedCode?: string;
  onSelect: (code: string) => void;
}

export interface CheckoutConnector {
  id: string;
  category: ConnectorCategory;
  /** Optional custom step UI; when omitted the default generic picker is used. */
  StepComponent?: ComponentType<CheckoutStepProps>;
  /** Map a chosen option code to the connectorSelections value sent at placeOrder (defaults to the code). */
  toSelection?: (code: string) => string;
}

const registry = new Map<string, CheckoutConnector>();

export function registerCheckoutConnector(connector: CheckoutConnector): void {
  registry.set(connector.id, connector);
}

export function getCheckoutConnector(id: string): CheckoutConnector | undefined {
  return registry.get(id);
}

export function getCheckoutConnectors(): CheckoutConnector[] {
  return Array.from(registry.values());
}
