package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider-neutral verification request. For a Razorpay checkout the frontend maps
 * razorpay_order_id -> gatewayOrderId, razorpay_payment_id -> gatewayPaymentId,
 * razorpay_signature -> signature.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPaymentRequest {
    private String gatewayOrderId;
    private String gatewayPaymentId;
    private String signature;
}
