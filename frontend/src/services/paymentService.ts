// GENERATED from the backend API contract — do not edit by hand.
// Typed client for the FOUNDATION-owned payment API — mirrors com.webappfoundation
// PaymentController (ApiInventory skips it, so the worker never derives this file).
// The webhook endpoint (POST /api/v1/payments/webhook) is server-to-server and is
// intentionally not exposed here.
import apiClient from '@/api/client';
import type {
  VerifyPaymentRequest,
  PaymentVerificationResponse,
} from '@/types/payment';

// There is intentionally NO public create-order call: a gateway order is opened server-side from the
// authoritative total (OrderService.placeOrder → PaymentService.createOrder). The client only verifies
// a completed payment. The webhook (POST /api/v1/payments/webhook) is server-to-server.

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
