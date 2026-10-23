package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-branch stock row, used only when a {@link Product} has {@code stockPerBranch=true}. Counts stock
 * independently for one {@code (productId, variantId, branchId)} combination; {@code variantId} is null
 * for a variant-less product. Available = {@code stock - reserved}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product_branch_stock",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "variant_id", "branch_id"}))
public class BranchStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Null for a variant-less product. */
    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false)
    @Builder.Default
    private Integer stock = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer reserved = 0;
}
