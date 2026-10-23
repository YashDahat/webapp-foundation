// FOUNDATION-owned order + workflow/connector types (mirror backend OrderDto / WorkflowDto / ConnectorDto).
// Orders run on a DB-configured workflow of stages; items are referenced polymorphically by (itemType, itemId).
import type { PaymentOrderResponse } from '@/types/payment';

export type FulfillmentType = 'DELIVERY' | 'SELF_PICKUP';
export type PaymentMode = 'ONLINE' | 'PAY_AT_COUNTER';
export type PaymentState = 'AWAITING_PAYMENT' | 'PAID' | 'FAILED' | 'REFUND_PENDING' | 'REFUNDED';
export type RefundPolicy = 'FULL' | 'PARTIAL' | 'NONE';
export type ConnectorCategory =
  | 'SHIPPING' | 'TAX' | 'DISCOUNT' | 'PAYMENT' | 'FULFILLMENT' | 'NOTIFICATION' | 'OTHER';

export interface Address {
  contactName?: string | null;
  phone?: string | null;
  line1?: string | null;
  line2?: string | null;
  city?: string | null;
  state?: string | null;
  pincode?: string | null;
  latitude?: number | null;
  longitude?: number | null;
}

export interface OrderItemDto {
  itemType: string;
  itemId: number;
  variantKey?: string | null;
  variantLabel?: string | null;
  name: string;
  unitPrice: number;
  quantity: number;
  imageUrl?: string | null;
}

export interface OrderAdjustmentDto {
  code: string;
  label: string;
  amount: number;
  connectorId?: string | null;
}

export interface OrderDto {
  id: number;
  orderNumber: string;
  referenceId?: string | null;
  branchId?: number | null;
  branchName?: string | null;
  fulfillmentType: FulfillmentType;
  paymentMode: PaymentMode;
  workflowCode: string;
  currentStageCode?: string | null;
  paymentState: PaymentState;
  subtotal: number;
  total: number;
  refundAmount?: number | null;
  cancelReason?: string | null;
  currency: string;
  shippingAddress?: Address | null;
  items: OrderItemDto[];
  adjustments: OrderAdjustmentDto[];
  cancellable: boolean;
  refundPreview?: number | null;
  feedbackSubmitted: boolean;
  createdAt: string;
  updatedAt: string;
  payment?: PaymentOrderResponse | null;
}

export interface CartLineInput {
  itemType: string;
  itemId: number;
  variantKey?: string | null;
  quantity: number;
}

export interface PlaceOrderRequest {
  items: CartLineInput[];
  branchId?: number | null;
  fulfillmentType: FulfillmentType;
  paymentMode: PaymentMode;
  addressId?: number | null;
  connectorSelections?: Record<string, string>;
}

export interface QuoteRequest {
  items: CartLineInput[];
  branchId?: number | null;
  fulfillmentType: FulfillmentType;
  addressId?: number | null;
}

export interface QuoteOption {
  connectorId: string;
  code: string;
  label: string;
  amount: number;
  detail?: string | null;
}

export interface QuoteResponse {
  subtotal: number;
  options: QuoteOption[];
}

// ── Admin: workflow + connectors ──
export interface ConnectorActionDto {
  code: string;
  label: string;
}

export interface ConnectorDto {
  id: string;
  displayName: string;
  category: ConnectorCategory;
  enabled: boolean;
  actions: ConnectorActionDto[];
  settings: Record<string, string>;
}

export interface WorkflowBindingDto {
  id?: number | null;
  connectorId: string;
  actionCode: string;
  ordinal: number;
  enabled: boolean;
  params?: Record<string, string>;
}

export interface WorkflowStageDto {
  id?: number | null;
  code: string;
  label: string;
  ordinal: number;
  initial: boolean;
  onPaidEntry: boolean;
  terminal: boolean;
  cancellable: boolean;
  refundPolicy: RefundPolicy;
  refundPercent?: number | null;
  bindings: WorkflowBindingDto[];
}

export interface WorkflowDto {
  code: string;
  label: string;
  /** Backend serializes the boolean `isDefault` field as `default`. */
  default: boolean;
  stages: WorkflowStageDto[];
}
