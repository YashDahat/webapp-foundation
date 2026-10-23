package com.webappfoundation.connector;

import com.webappfoundation.model.Address;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** Context passed to {@link ActionConnector#execute} when an order enters a stage (async, post-commit). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStageContext {
    private Long orderId;
    private String referenceId;
    private Integer userId;
    private String stageCode;
    private List<OrderItemSnapshot> items;
    private Address shippingAddress;
    private Map<String, String> params;
}
