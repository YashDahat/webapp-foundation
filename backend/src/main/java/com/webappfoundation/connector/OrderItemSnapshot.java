package com.webappfoundation.connector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** An immutable snapshot of one order line passed to connectors (no JPA entity leaks across the SPI). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemSnapshot {
    private String itemType;
    private Long itemId;
    private String variantKey;
    private String name;
    private BigDecimal unitPrice;
    private int quantity;
}
