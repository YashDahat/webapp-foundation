package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Body for a standalone (goodwill) refund that does NOT cancel the order — distinct from the stage-aware
 * refund inside {@code cancel()}. {@code amount == null} refunds the full order total.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundOrderRequest {
    private BigDecimal amount;
    private String reason;
}
