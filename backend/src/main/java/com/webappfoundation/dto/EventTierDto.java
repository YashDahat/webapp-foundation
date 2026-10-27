package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** A ticket tier with its computed {@code available} seats ({@code stock - reserved}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventTierDto {
    private Long id;
    private String label;
    private String sku;
    private BigDecimal price;
    private Integer stock;
    private Integer reserved;
    private Integer available;
}
