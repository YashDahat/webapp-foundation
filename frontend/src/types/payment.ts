// GENERATED from the backend API contract — do not edit by hand.
// Source of truth: webapp-foundation backend (com.webappfoundation.dto / .model),
// renamed to the project package at clone time. Payment is FOUNDATION-OWNED: ApiInventory
// skips PaymentController, so the worker never auto-derives these — they live here so the
// frontend (and generated code) has real, importable types for the payment API surface.
// ApiContractCard picks this file up via the marker line above and feeds it to the LLM.

/** Payment lifecycle status — com.webappfoundation.model.PaymentStatus. */
export type PaymentStatus = 'CREATED' | 'CAPTURED' | 'FAILED';

/** Request body for POST /api/v1/payments/create-order — CreatePaymentRequest. */
export interface CreatePaymentRequest {
  /** BigDecimal on the wire — amount in major currency units (e.g. rupees). */
  amount: number;
  currency: string;
  referenceId: string;
}

/** Response body for POST /api/v1/payments/create-order — PaymentOrderResponse. */
export interface PaymentOrderResponse {
  gatewayOrderId: string;
  gatewayKeyId: string;
  /** int on the wire — amount in the smallest currency unit (e.g. paise). */
  amount: number;
  currency: string;
  paymentRecordId: number;
}

/** Request body for POST /api/v1/payments/verify — VerifyPaymentRequest. */
export interface VerifyPaymentRequest {
  gatewayOrderId: string;
  gatewayPaymentId: string;
  signature: string;
}

/** Response body for POST /api/v1/payments/verify — PaymentVerificationResponse. */
export interface PaymentVerificationResponse {
  verified: boolean;
  status: string;
  referenceId: string;
}

/** Persisted payment record — com.webappfoundation.model.Payment. */
export interface Payment {
  id: number;
  referenceId: string;
  gatewayOrderId: string;
  gatewayPaymentId: string;
  amount: number;
  currency: string;
  status: PaymentStatus;
  /** Instant on the wire — ISO-8601 timestamp string. */
  createdAt: string;
}
