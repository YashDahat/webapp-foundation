package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider-neutral order response. For a Razorpay checkout widget the frontend maps
 * gatewayOrderId -> order_id and gatewayKeyId -> key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderResponse {
    /** Gateway order id to hand to the checkout widget. */
    private String gatewayOrderId;
    /** Gateway public key id the frontend needs to open checkout. */
    private String gatewayKeyId;
    /** Amount in the minor unit (paise) — what the checkout widget expects. */
    private int amount;
    private String currency;
    /** The local Payment row id, for correlating the later verify call. */
    private Long paymentRecordId;
}
