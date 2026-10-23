package com.webappfoundation.inventory;

import com.webappfoundation.model.BranchScope;
import com.webappfoundation.model.BranchStock;
import com.webappfoundation.model.Product;
import com.webappfoundation.model.ProductVariant;
import com.webappfoundation.repository.BranchStockRepository;
import com.webappfoundation.repository.ProductRepository;
import com.webappfoundation.repository.ProductVariantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.webappfoundation.catalog.CatalogConstants.PRODUCT;

/**
 * The shipped {@link InventoryProvider} for the default {@link Product} ({@code itemType="PRODUCT"}).
 *
 * <p>Unlimited products ({@code stockTracked=false}) make every stock op a no-op. For tracked stock,
 * reservations use a single <b>atomic conditional update</b>
 * ({@code SET reserved = reserved + :qty WHERE (stock - reserved) >= :qty}) on the shared row (product, or
 * variant row when variants exist), or on the per-branch {@link BranchStock} row when
 * {@code stockPerBranch=true} — so there is <b>no oversell under concurrency</b> and no pessimistic lock.
 * {@code reserved} holds both held and sold units ({@code available = stock - reserved}), so {@code commit}
 * is a no-op on counts and {@code release} uniformly reverses a hold (cancel/expiry) or a sold unit (return).
 * A shortfall throws {@link ResponseStatusException} CONFLICT (auto-mapped to 409).
 *
 * <p>{@code variantKey} is the chosen {@link ProductVariant}'s id as a string.
 */
@Component
public class ProductInventoryProvider implements InventoryProvider {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final BranchStockRepository branchStockRepository;

    public ProductInventoryProvider(ProductRepository productRepository,
                                    ProductVariantRepository variantRepository,
                                    BranchStockRepository branchStockRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.branchStockRepository = branchStockRepository;
    }

    @Override
    public String itemType() {
        return PRODUCT;
    }

    @Override
    public boolean availableAt(Long itemId, Long branchId) {
        Product p = productRepository.findById(itemId).orElse(null);
        if (p == null || !p.isActive()) {
            return false;
        }
        if (branchId == null || p.getBranchScope() == BranchScope.GLOBAL) {
            return true;
        }
        return p.getAvailableBranchIds() != null && p.getAvailableBranchIds().contains(branchId);
    }

    @Override
    @Transactional
    public void reserve(Long itemId, String variantKey, Long branchId, int qty) {
        if (qty <= 0) {
            return;
        }
        Product p = productRepository.findById(itemId)
                .orElseThrow(() -> conflict("Item no longer available"));
        Long variantId = parseVariantId(variantKey);

        if (variantId != null) {
            ProductVariant v = findVariant(p, variantId);
            if (!v.isStockTracked()) {
                return; // unlimited variant
            }
            if (p.isStockPerBranch()) {
                reserveBranchRow(p.getId(), variantId, branchId, qty);
            } else if (variantRepository.reserveIfAvailable(variantId, qty) == 0) {
                throw conflict("Not enough stock for " + p.getName() + " (" + v.getLabel() + ")");
            }
            return;
        }

        if (!p.isStockTracked()) {
            return; // unlimited
        }
        if (p.isStockPerBranch()) {
            reserveBranchRow(p.getId(), null, branchId, qty);
        } else if (productRepository.reserveIfAvailable(itemId, qty) == 0) {
            throw conflict("Not enough stock for " + p.getName());
        }
    }

    @Override
    public void commit(Long itemId, String variantKey, Long branchId, int qty) {
        // No-op on counts: a held unit stays counted in `reserved` once sold (available = stock - reserved).
    }

    @Override
    @Transactional
    public void release(Long itemId, String variantKey, Long branchId, int qty) {
        if (qty <= 0) {
            return;
        }
        Product p = productRepository.findById(itemId).orElse(null);
        if (p == null || !p.isStockTracked()) {
            return;
        }
        Long variantId = parseVariantId(variantKey);
        if (p.isStockPerBranch()) {
            branchStockRepository.findByProductIdAndVariantIdAndBranchId(p.getId(), variantId, branchId)
                    .ifPresent(row -> branchStockRepository.release(row.getId(), qty));
        } else if (variantId != null) {
            variantRepository.release(variantId, qty);
        } else {
            productRepository.release(itemId, qty);
        }
    }

    private void reserveBranchRow(Long productId, Long variantId, Long branchId, int qty) {
        BranchStock row = branchStockRepository
                .findByProductIdAndVariantIdAndBranchId(productId, variantId, branchId)
                .orElseThrow(() -> conflict("Not stocked at this branch"));
        if (branchStockRepository.reserveIfAvailable(row.getId(), qty) == 0) {
            throw conflict("Not enough stock at this branch");
        }
    }

    private ProductVariant findVariant(Product p, Long variantId) {
        return p.getVariants().stream()
                .filter(v -> variantId.equals(v.getId()))
                .findFirst()
                .orElseThrow(() -> conflict("Unknown variant"));
    }

    private Long parseVariantId(String variantKey) {
        if (variantKey == null || variantKey.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(variantKey.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
