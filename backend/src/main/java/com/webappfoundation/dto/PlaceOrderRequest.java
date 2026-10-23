package com.webappfoundation.dto;

import com.webappfoundation.model.FulfillmentType;
import com.webappfoundation.model.PaymentMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Place-order payload. {@code addressId} references a saved UserAddress (required for DELIVERY, omitted for
 * SELF_PICKUP); the server snapshots it into {@code Order.shippingAddress}. {@code connectorSelections} maps
 * a connectorId to the chosen quote option code (re-validated server-side). No prices are trusted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceOrderRequest {
    private List<CartLineInput> items;
    private Long branchId;
    private FulfillmentType fulfillmentType;
    private PaymentMode paymentMode;
    private Long addressId;
    private Map<String, String> connectorSelections;
}
