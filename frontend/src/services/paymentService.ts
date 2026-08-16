// GENERATED from the backend API contract — do not edit by hand.
// Typed client for the FOUNDATION-owned payment API — mirrors com.webappfoundation
// PaymentController (ApiInventory skips it, so the worker never derives this file).
// The webhook endpoint (POST /api/v1/payments/webhook) is server-to-server and is
// intentionally not exposed here.
import apiClient from '@/api/client';
import type {
  CreatePaymentRequest,
  PaymentOrderResponse,
  VerifyPaymentRequest,
  PaymentVerificationResponse,
} from '@/types/payment';

/** POST /api/v1/payments/create-order — open a gateway order for the given amount. */
export const createPaymentOrder = async (
  request: CreatePaymentRequest,
): Promise<PaymentOrderResponse> => {
  const response = await apiClient.post<PaymentOrderResponse>(
    '/api/v1/payments/create-order',
    request,
  );
  return response.data;
};

/** POST /api/v1/payments/verify — confirm a completed gateway payment's signature. */
export const verifyPayment = async (
  request: VerifyPaymentRequest,
): Promise<PaymentVerificationResponse> => {
  const response = await apiClient.post<PaymentVerificationResponse>(
    '/api/v1/payments/verify',
    request,
  );
  return response.data;
};
