package com.webappfoundation.connector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** A priced option a {@link QuoteConnector} offers at checkout; becomes an {@code OrderAdjustment} if chosen. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteOption {
    private String connectorId;
    private String code;
    private String label;
    private BigDecimal amount;
    private String detail;
}
