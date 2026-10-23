package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** Checkout quote result: the re-priced subtotal plus the priced options connectors offer. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteResponse {
    private BigDecimal subtotal;
    private List<Option> options;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Option {
        private String connectorId;
        private String code;
        private String label;
        private BigDecimal amount;
        private String detail;
    }
}
