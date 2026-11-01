package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** The result of validating a discount code against a cart (checkout "have a code?" preview). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferValidationResult {
    private boolean valid;
    private String code;
    private BigDecimal amount;      // positive rupees that would come off (0 for non-cash)
    private String message;        // reason when invalid, or a friendly confirmation

    public static OfferValidationResult invalid(String code, String message) {
        return OfferValidationResult.builder().valid(false).code(code).amount(BigDecimal.ZERO).message(message).build();
    }
}
