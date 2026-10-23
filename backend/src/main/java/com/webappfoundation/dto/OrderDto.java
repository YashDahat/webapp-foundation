package com.webappfoundation.dto;

import com.webappfoundation.model.Address;
import com.webappfoundation.model.FulfillmentType;
import com.webappfoundation.model.PaymentMode;
import com.webappfoundation.model.PaymentState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Customer/admin view of an order. {@code payment} is non-null only for an online order awaiting payment
 * (the gateway order the client checks out with). {@code cancellable}/{@code refundPreview} reflect the
 * current stage's cancellation policy so the UI can show/hide the cancel button with the refund it grants.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {
    private Long id;
    private String orderNumber;
    private String referenceId;
    private Long branchId;
    private String branchName;
    private FulfillmentType fulfillmentType;
    private PaymentMode paymentMode;
    private String workflowCode;
    private String currentStageCode;
    private PaymentState paymentState;
    private BigDecimal subtotal;
    private BigDecimal total;
    private BigDecimal refundAmount;
    private String cancelReason;
    private String currency;
    private Address shippingAddress;
    private List<OrderItemDto> items;
    private List<OrderAdjustmentDto> adjustments;
    private boolean cancellable;
    private BigDecimal refundPreview;
    private boolean feedbackSubmitted;
    private Instant createdAt;
    private Instant updatedAt;

    /** Gateway order to check out with (online, awaiting payment); null otherwise. */
    private PaymentOrderResponse payment;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDto {
        private String itemType;
        private Long itemId;
        private String variantKey;
        private String variantLabel;
        private String name;
        private BigDecimal unitPrice;
        private int quantity;
        private String imageUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderAdjustmentDto {
        private String code;
        private String label;
        private BigDecimal amount;
        private String connectorId;
    }
}
