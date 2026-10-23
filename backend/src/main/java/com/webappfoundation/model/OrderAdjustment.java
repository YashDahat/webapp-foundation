package com.webappfoundation.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A single priced adjustment on an {@link Order} (shipping fee, tax, discount), contributed by a connector.
 * Signed amount: + for fees/tax, − for discounts. Mirrors the cart's adjustments concept.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class OrderAdjustment {
    private String code;
    private String label;
    private BigDecimal amount;
    private String connectorId;
}
