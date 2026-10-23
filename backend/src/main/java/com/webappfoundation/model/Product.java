package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The batteries-included default sellable, so simple commerce businesses work out of the box. Richer
 * businesses keep their own typed entity ({@code Room}, {@code Membership}) and map it to the same
 * {@code CatalogItemDto} contract via a {@code CatalogItemResolver} — this entity maps via
 * {@code ProductCatalogResolver} ({@code itemType="PRODUCT"}).
 *
 * <p>Branch availability, opt-in stock (shared or per-branch), and optional variants/sizes are all set on
 * the admin form. {@code workflowCode} selects which order lifecycle + cancellation/refund policy this
 * product follows (default = the default workflow).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    // ── Branch availability (set when adding the product) ──

    /** GLOBAL (default, offered at every branch) or SELECTED (only {@link #availableBranchIds}). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BranchScope branchScope = BranchScope.GLOBAL;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_branches", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "branch_id")
    @Builder.Default
    private Set<Long> availableBranchIds = new LinkedHashSet<>();

    // ── Stock (opt-in per product) ──

    /** false (default) = unlimited (e.g. food); true = track a {@code stock - reserved} pool. */
    @Column(nullable = false)
    @Builder.Default
    private boolean stockTracked = false;

    /** When true (and tracked), stock is counted per branch via {@link BranchStock} instead of a shared pool. */
    @Column(nullable = false)
    @Builder.Default
    private boolean stockPerBranch = false;

    /** Shared-pool stock cap (used when tracked and not per-branch, and the product has no variants). */
    private Integer stock;

    @Builder.Default
    private Integer reserved = 0;

    // ── Variants / sizes (optional) ──

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id")
    @OrderColumn(name = "variant_order")
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();

    // ── Media (media-library URLs only; no byte storage) ──

    /** Thumbnail / primary image. */
    private String imageUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "image_order")
    @Column(name = "image_url")
    @Builder.Default
    private List<String> images = new ArrayList<>();

    private String category;

    /** Short highlight bullets shown on the product detail page (e.g. "Water-resistant", "2-year warranty"). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_features", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "feature_order")
    @Column(name = "feature", columnDefinition = "text")
    @Builder.Default
    private List<String> features = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Which order lifecycle + cancellation/refund policy this product follows (default = default workflow). */
    private String workflowCode;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
