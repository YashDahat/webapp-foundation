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
  /** Time target (minutes) for the order-board "stuck" flag; null = no target. */
  targetMinutes?: number | null;
  bindings: WorkflowBindingDto[];
}

/** Removed-stage → surviving-stage remap required when a workflow edit drops a stage live orders occupy. */
export interface WorkflowStageMappingRequest {
  mappings: Record<string, string>;
}

export interface WorkflowDto {
  code: string;
  label: string;
  /** Backend serializes the boolean `isDefault` field as `default`. */
  default: boolean;
  /** Label for this workflow's assignee role on the board (e.g. "Rider", "Packer"); null = none. */
  staffRole?: string | null;
  /** Optional in-flight order migration map sent with a workflow edit (removed → surviving stage code). */
  stageMapping?: Record<string, string> | null;
  stages: WorkflowStageDto[];
}

// ── Order board (admin kanban over the order spine) — mirror backend board DTOs ──
export type BlockReason =
  | 'OUT_OF_STOCK' | 'CUSTOMER_UNREACHABLE' | 'PAYMENT_ISSUE'
  | 'STAFF_UNAVAILABLE' | 'ADDRESS_ISSUE' | 'OTHER';

export type TriggerType = 'SYSTEM' | 'PAYMENT' | 'WEBHOOK' | 'ADMIN' | 'RETRY';

/** Column kind drives the board's valid drop-target rules. */
export type ColumnKind = 'STAGE' | 'BLOCKED' | 'CANCELLED';

export interface OrderCardDto {
  id: number;
  orderNumber: string;
  customerName?: string | null;
  total: number;
  currency: string;
  itemCount: number;
  fulfillmentType: FulfillmentType;
  paymentMode: PaymentMode;
  paymentState: PaymentState;
  branchId?: number | null;
  branchName?: string | null;
  assignedUserId?: number | null;
  assignedStaffName?: string | null;
  blocked: boolean;
  blockReason?: BlockReason | null;
  channel?: string | null;
  stageCode: string;
  stageEnteredAt?: string | null;
  blockedAt?: string | null;
  targetMinutes?: number | null;
  minutesInStage: number;
  stuck: boolean;
  carriedForward: boolean;
  createdAt: string;
}

export interface BoardColumnDto {
  kind: ColumnKind;
  stageCode?: string | null;
  label: string;
  ordinal?: number | null;
  targetMinutes?: number | null;
  count: number;
  stuckCount: number;
  cards: OrderCardDto[];
}

export interface BoardFlagCounts {
  blocked: number;
  stuck: number;
  unpaid: number;
  unassigned: number;
}

export interface BoardBranchCount {
  branchId?: number | null;
  branchName?: string | null;
  count: number;
}

export interface OrderBoardDto {
  workflowCode: string;
  workflowLabel: string;
  staffRole?: string | null;
  sprint?: import('@/types/sprint').SprintDto | null;
  branchId?: number | null;
  columns: BoardColumnDto[];
  flags: BoardFlagCounts;
  branches: BoardBranchCount[];
  generatedAt: string;
}

export interface OrderStageEventDto {
  id: number;
  stageCode: string;
  stageLabel: string;
  enteredAt: string;
  trigger: TriggerType;
  byUserId?: number | null;
  migration: boolean;
}

/** Board detail-sheet read model: the customer OrderDto + board overlay + stage-history timeline. */
export interface OrderBoardDetailDto {
  order: OrderDto;
  customerName?: string | null;
  stageLabel?: string | null;
  assignedUserId?: number | null;
  assignedStaffName?: string | null;
  blocked: boolean;
  blockReason?: BlockReason | null;
  blockNote?: string | null;
  blockedAt?: string | null;
  stageEnteredAt?: string | null;
  targetMinutes?: number | null;
  minutesInStage: number;
  stuck: boolean;
  carriedForward: boolean;
  sprintId?: number | null;
  channel?: string | null;
  timeline: OrderStageEventDto[];
}

export interface BlockOrderRequest {
  reason: BlockReason;
  note?: string | null;
}

export interface AssignOrderRequest {
  /** null unassigns. */
  userId?: number | null;
}

export interface RefundOrderRequest {
  /** null refunds the full order total. */
  amount?: number | null;
  reason?: string | null;
}
