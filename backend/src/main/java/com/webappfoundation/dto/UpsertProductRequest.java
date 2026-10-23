package com.webappfoundation.dto;

import com.webappfoundation.model.BranchScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Create/update payload for the admin product form. {@code slug} is derived from {@code name} when blank.
 * When {@code stockPerBranch} is set, per-branch counts come from {@code branchStock}; otherwise {@code stock}
 * is the shared-pool cap. Variants, when present, make the product non-cartable (pick a size on the detail page).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertProductRequest {
    private String name;
    private String slug;
    private String description;
    private BigDecimal price;
    private String category;
    private Boolean active;
    private String workflowCode;

    @Builder.Default
    private BranchScope branchScope = BranchScope.GLOBAL;
    private Set<Long> availableBranchIds;

    // Nullable so a partial payload (omitting a toggle) doesn't 400 on primitive deserialization;
    // read null-safely in the service (absent = false).
    private Boolean stockTracked;
    private Boolean stockPerBranch;
    private Integer stock;

    private String imageUrl;
    private List<String> images;
    private List<String> features;

    private List<VariantInput> variants;
    private List<BranchStockInput> branchStock;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantInput {
        private Long id;          // present on update of an existing variant
        private String label;
        private String sku;
        private BigDecimal price;
        private Boolean stockTracked;
        private Integer stock;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BranchStockInput {
        private Long variantId;   // null for a variant-less product
        private Long branchId;
        private Integer stock;
    }
}
