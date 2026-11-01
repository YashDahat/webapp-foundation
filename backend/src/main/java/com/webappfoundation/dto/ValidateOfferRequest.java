package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Checkout preview request: a typed code + the current cart subtotal. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateOfferRequest {
    private String code;
    private BigDecimal subtotal;
}
