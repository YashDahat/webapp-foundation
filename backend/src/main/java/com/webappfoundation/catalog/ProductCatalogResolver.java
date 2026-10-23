package com.webappfoundation.catalog;

import com.webappfoundation.dto.CatalogItemDto;
import com.webappfoundation.model.BranchStock;
import com.webappfoundation.model.Product;
import com.webappfoundation.model.ProductVariant;
import com.webappfoundation.repository.BranchStockRepository;
import com.webappfoundation.repository.ProductRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

import static com.webappfoundation.catalog.CatalogConstants.PRODUCT;

/**
 * The one shipped {@link CatalogItemResolver} ({@code itemType="PRODUCT"}): maps the default
 * {@link Product} entity to the universal {@link CatalogItemDto}. A new business adds its own resolver
 * alongside this one. {@code detailPath = "/products/<slug>"} and {@code cartable} is true only for a
 * variant-less, in-stock product (a product with variants needs a size picked on the detail page).
 */
@Component
public class ProductCatalogResolver implements CatalogItemResolver {

    private final ProductRepository productRepository;
    private final BranchStockRepository branchStockRepository;

    public ProductCatalogResolver(ProductRepository productRepository,
                                  BranchStockRepository branchStockRepository) {
        this.productRepository = productRepository;
        this.branchStockRepository = branchStockRepository;
    }

    @Override
    public String itemType() {
        return PRODUCT;
    }

    @Override
    public List<CatalogItemDto> resolve(Collection<Long> ids) {
        return productRepository.findByIdIn(ids).stream()
                .filter(Product::isActive)
                .map(this::toDto)
                .toList();
    }

    private CatalogItemDto toDto(Product p) {
        boolean hasVariants = p.getVariants() != null && !p.getVariants().isEmpty();
        return CatalogItemDto.builder()
                .itemType(PRODUCT)
                .itemId(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .imageUrl(p.getImageUrl())
                .images(p.getImages())
                .slug(p.getSlug())
                .detailPath("/products/" + p.getSlug())
                .category(p.getCategory())
                .active(p.isActive())
                .cartable(!hasVariants && inStock(p))
                .workflowCode(p.getWorkflowCode())
                .build();
    }

    /** True when the product can be bought now (unlimited, or has availability in its stock model). */
    private boolean inStock(Product p) {
        if (p.getVariants() != null && !p.getVariants().isEmpty()) {
            return p.getVariants().stream().anyMatch(v -> variantInStock(p, v));
        }
        if (!p.isStockTracked()) {
            return true; // unlimited
        }
        if (p.isStockPerBranch()) {
            return branchStockRepository.findByProductId(p.getId()).stream()
                    .anyMatch(ProductCatalogResolver::rowAvailable);
        }
        return available(p.getStock(), p.getReserved()) > 0;
    }

    private boolean variantInStock(Product p, ProductVariant v) {
        if (!v.isStockTracked()) {
            return true;
        }
        if (p.isStockPerBranch()) {
            return branchStockRepository.findByProductId(p.getId()).stream()
                    .filter(b -> v.getId() != null && v.getId().equals(b.getVariantId()))
                    .anyMatch(ProductCatalogResolver::rowAvailable);
        }
        return available(v.getStock(), v.getReserved()) > 0;
    }

    private static boolean rowAvailable(BranchStock b) {
        return available(b.getStock(), b.getReserved()) > 0;
    }

    private static int available(Integer stock, Integer reserved) {
        return (stock == null ? 0 : stock) - (reserved == null ? 0 : reserved);
    }
}
