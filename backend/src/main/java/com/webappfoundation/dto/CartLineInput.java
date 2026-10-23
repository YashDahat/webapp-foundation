package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One requested cart line. Carries NO prices — the server re-prices via the catalog. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartLineInput {
    private String itemType;
    private Long itemId;
    private String variantKey;
    private int quantity;
}
