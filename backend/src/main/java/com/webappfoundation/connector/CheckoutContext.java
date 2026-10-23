package com.webappfoundation.connector;

import com.webappfoundation.model.Address;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Context passed to {@link QuoteConnector#quote} at checkout time (synchronous, pre-order). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutContext {
    private Integer userId;
    private List<OrderItemSnapshot> items;
    private BigDecimal subtotal;
    private Address shippingAddress;
    private Map<String, String> selections;
}
