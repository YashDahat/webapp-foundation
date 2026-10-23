package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** A product option (size/volume). {@code available} is computed (shared pool); null when untracked. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantDto {
    private Long id;
    private String label;
    private String sku;
    private BigDecimal price;
    private boolean stockTracked;
    private Integer stock;
    private Integer reserved;
    private Integer available;
}
