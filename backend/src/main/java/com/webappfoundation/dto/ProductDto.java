package com.webappfoundation.dto;

import com.webappfoundation.model.BranchScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * The admin/detail shape for the default {@link com.webappfoundation.model.Product}. {@code available}
 * is computed (shared pool = {@code stock - reserved}); it is null for unlimited or per-branch stock.
 * Each variant carries its own computed {@code available}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private BigDecimal price;

    private BranchScope branchScope;
    private Set<Long> availableBranchIds;

    private boolean stockTracked;
    private boolean stockPerBranch;
    private Integer stock;
    private Integer reserved;
    private Integer available;

    private List<ProductVariantDto> variants;

    private String imageUrl;
    private List<String> images;
    private List<String> features;
    private String category;
    private boolean active;
    private String workflowCode;

    private Instant createdAt;
    private Instant updatedAt;
}
