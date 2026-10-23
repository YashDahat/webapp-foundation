package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * An optional product option such as size ("S"/"M"/"L") or volume ("500ml"). When a {@link Product} has
 * variants it is {@code cartable=false} (the buyer picks a variant on the detail page) and — when stock
 * is tracked — stock is counted <b>per variant</b> (× branch when {@code stockPerBranch}). {@code price}
 * null → inherit the product price. The {@code stock}/{@code reserved} fields hold the shared pool;
 * per-branch counts live in {@link BranchStock}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product_variant")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The option label, e.g. "S"/"M"/"L" or "500ml". */
    @Column(nullable = false)
    private String label;

    private String sku;

    /** Null → inherit the owning product's price. */
    private BigDecimal price;

    @Column(nullable = false)
    @Builder.Default
    private boolean stockTracked = false;

    /** Shared-pool held/available stock for this variant (per-branch counts live in BranchStock). */
    private Integer stock;

    @Builder.Default
    private Integer reserved = 0;
}
