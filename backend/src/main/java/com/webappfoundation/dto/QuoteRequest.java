package com.webappfoundation.dto;

import com.webappfoundation.model.FulfillmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Checkout-time quote request: run quote connectors for the current cart + fulfilment + address. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteRequest {
    private List<CartLineInput> items;
    private Long branchId;
    private FulfillmentType fulfillmentType;
    private Long addressId;
}
