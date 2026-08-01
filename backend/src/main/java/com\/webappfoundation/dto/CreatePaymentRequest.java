package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {
    /** Amount in the major currency unit (e.g. rupees), not paise. */
    private BigDecimal amount;
    /** ISO currency code; defaults to INR when null. */
    private String currency;
    /** Opaque reference to the domain entity being paid for (e.g. "order_42"). */
    private String referenceId;
}
