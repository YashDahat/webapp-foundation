package com.webappfoundation.service;

import com.webappfoundation.dto.BulkUploadResult;
import com.webappfoundation.dto.ProductDto;
import com.webappfoundation.dto.ProductVariantDto;
import com.webappfoundation.dto.UpsertProductRequest;
import com.webappfoundation.exception.ResourceNotFoundException;
import com.webappfoundation.model.BranchScope;
import com.webappfoundation.model.BranchStock;
import com.webappfoundation.model.Product;
import com.webappfoundation.model.ProductVariant;
import com.webappfoundation.repository.BranchRepository;
import com.webappfoundation.repository.BranchStockRepository;
import com.webappfoundation.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CRUD + bulk-import for the default {@link Product} sellable. Public reads return only active products
 * (optionally filtered by category, search, and the selected branch); admin writes are audited. Images are
 * media-library URLs only (no byte storage / upload endpoint — those live in the media library).
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final BranchStockRepository branchStockRepository;
    private final BranchRepository branchRepository;
    private final SpreadsheetParser spreadsheetParser;
    private final AuditService auditService;

    public ProductService(ProductRepository productRepository, BranchStockRepository branchStockRepository,
                          BranchRepository branchRepository, SpreadsheetParser spreadsheetParser,
                          AuditService auditService) {
        this.productRepository = productRepository;
        this.branchStockRepository = branchStockRepository;
        this.branchRepository = branchRepository;
        this.spreadsheetParser = spreadsheetParser;
        this.auditService = auditService;
    }

    // ── Public reads ──

    public List<ProductDto> listActive(String category, String q, Long branchId) {
        List<Product> products;
        if (q != null && !q.isBlank()) {
            products = productRepository.findByActiveTrueAndNameContainingIgnoreCase(q.trim());
        } else if (category != null && !category.isBlank()) {
            products = productRepository.findByActiveTrueAndCategory(category.trim());
        } else {
            products = productRepository.findByActiveTrue();
        }
        return products.stream()
                .filter(p -> offeredAtBranch(p, branchId))
                .map(this::toDto)
                .toList();
    }

    public ProductDto getById(Long id) {
        return toDto(productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id)));
    }

    public ProductDto getBySlug(String slug) {
        return toDto(productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + slug)));
    }

    // ── Admin reads/writes ──

    public List<ProductDto> adminList() {
        return productRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public ProductDto create(UpsertProductRequest req) {
        validateUpsert(req);
        String slug = resolveSlug(req, null);
        if (productRepository.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A product with slug '" + slug + "' already exists.");
        }
        if (productRepository.existsByNameIgnoreCase(req.getName().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A product named '" + req.getName().trim() + "' already exists.");
        }
        Instant now = Instant.now();
        Product product = Product.builder()
                .slug(slug)
                .createdAt(now)
                .updatedAt(now)
                .build();
        apply(product, req);
        Product saved = productRepository.save(product);
        replaceBranchStock(saved, req);
        auditService.record(AuditActions.PRODUCT_CREATED, AuditActions.SUCCESS, "PRODUCT",
                String.valueOf(saved.getId()), "name=" + saved.getName());
        return toDto(saved);
    }

    @Transactional
    public ProductDto update(Long id, UpsertProductRequest req) {
        validateUpsert(req);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        String slug = resolveSlug(req, product);
        if (!slug.equals(product.getSlug()) && productRepository.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A product with slug '" + slug + "' already exists.");
        }
        product.setSlug(slug);
        apply(product, req);
        product.setUpdatedAt(Instant.now());
        Product saved = productRepository.save(product);
        replaceBranchStock(saved, req);
        auditService.record(AuditActions.PRODUCT_UPDATED, AuditActions.SUCCESS, "PRODUCT",
                String.valueOf(saved.getId()), "name=" + saved.getName());
        return toDto(saved);
    }

    @Transactional
    public void delete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        branchStockRepository.deleteByProductId(id);
        productRepository.delete(product);
        auditService.record(AuditActions.PRODUCT_DELETED, AuditActions.SUCCESS, "PRODUCT",
                String.valueOf(id), "name=" + product.getName());
    }

    /**
     * Bulk-import variant-less, shared-stock products from a .csv/.xlsx. Columns:
     * {@code name, price, category, stockTracked, stock, workflowCode, branchSlugs} (all but name/price
     * optional). Empty {@code branchSlugs} → GLOBAL. Duplicate slug/name rows are skipped.
     */
    @Transactional
    public BulkUploadResult bulkImport(MultipartFile file) {
        BulkUploadResult result = BulkUploadResult.builder().build();
        List<Map<String, String>> rows = spreadsheetParser.parse(file);
        int rowNum = 1; // header is row 1; data starts at 2
        for (Map<String, String> row : rows) {
            rowNum++;
            String name = row.getOrDefault("name", "").trim();
            String priceRaw = row.getOrDefault("price", "").trim();
            if (name.isEmpty() || priceRaw.isEmpty()) {
                result.addError(rowNum, "name and price are required");
                continue;
            }
            BigDecimal price;
            try {
                price = new BigDecimal(priceRaw);
                if (price.signum() < 0) {
                    result.addError(rowNum, "price must not be negative");
                    continue;
                }
            } catch (NumberFormatException e) {
                result.addError(rowNum, "price is not a number: " + priceRaw);
                continue;
            }
            String slug = slugify(name);
            if (productRepository.existsBySlug(slug) || productRepository.existsByNameIgnoreCase(name)) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            boolean stockTracked = truthy(row.getOrDefault("stocktracked", ""));
            Integer stock = null;
            if (stockTracked) {
                String stockRaw = row.getOrDefault("stock", "").trim();
                try {
                    stock = Integer.parseInt(stockRaw);
                    if (stock < 0) {
                        result.addError(rowNum, "stock must be a non-negative integer");
                        continue;
                    }
                } catch (NumberFormatException e) {
                    result.addError(rowNum, "stock must be a non-negative integer when stockTracked is true");
                    continue;
                }
            }
            // branchSlugs (comma-separated) → SELECTED branches; empty → GLOBAL (all branches).
            String branchSlugs = row.getOrDefault("branchslugs", "").trim();
            BranchScope branchScope = BranchScope.GLOBAL;
            Set<Long> branchIds = new LinkedHashSet<>();
            if (!branchSlugs.isEmpty()) {
                boolean unknown = false;
                for (String s : branchSlugs.split(",")) {
                    String bs = s.trim();
                    if (bs.isEmpty()) continue;
                    Long bid = branchRepository.findBySlug(bs).map(b -> b.getId()).orElse(null);
                    if (bid == null) {
                        result.addError(rowNum, "unknown branch slug: " + bs);
                        unknown = true;
                        break;
                    }
                    branchIds.add(bid);
                }
                if (unknown) continue;
                if (!branchIds.isEmpty()) branchScope = BranchScope.SELECTED;
            }
            Instant now = Instant.now();
            productRepository.save(Product.builder()
                    .name(name)
                    .slug(slug)
                    .price(price)
                    .category(emptyToNull(row.getOrDefault("category", "")))
                    .stockTracked(stockTracked)
                    .stock(stock)
                    .reserved(0)
                    .workflowCode(emptyToNull(row.getOrDefault("workflowcode", "")))
                    .branchScope(branchScope)
                    .availableBranchIds(branchIds)
                    .active(true)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            result.setCreated(result.getCreated() + 1);
        }
        auditService.record(AuditActions.PRODUCT_BULK_IMPORT, AuditActions.SUCCESS, "PRODUCT", null,
                "created=" + result.getCreated() + " skipped=" + result.getSkipped() + " failed=" + result.getFailed());
        return result;
    }

    // ── Helpers ──

    private void validateUpsert(UpsertProductRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        if (req.getPrice() == null || req.getPrice().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a non-negative price is required");
        }
    }

    /** Copy editable fields (except slug/timestamps) from the request onto the entity, reconciling variants. */
    private void apply(Product p, UpsertProductRequest req) {
        p.setName(req.getName().trim());
        p.setDescription(emptyToNull(req.getDescription()));
        p.setPrice(req.getPrice());
        p.setCategory(emptyToNull(req.getCategory()));
        p.setActive(req.getActive() == null || req.getActive());
        p.setWorkflowCode(emptyToNull(req.getWorkflowCode()));

        p.setBranchScope(req.getBranchScope() == null ? BranchScope.GLOBAL : req.getBranchScope());
        p.setAvailableBranchIds(p.getBranchScope() == BranchScope.SELECTED && req.getAvailableBranchIds() != null
                ? new LinkedHashSet<>(req.getAvailableBranchIds())
                : new LinkedHashSet<>());

        boolean tracked = Boolean.TRUE.equals(req.getStockTracked());
        boolean perBranch = Boolean.TRUE.equals(req.getStockPerBranch());
        p.setStockTracked(tracked);
        p.setStockPerBranch(perBranch);
        p.setStock(tracked && !perBranch ? req.getStock() : null);
        if (p.getReserved() == null) {
            p.setReserved(0);
        }

        p.setImageUrl(emptyToNull(req.getImageUrl()));
        p.setImages(req.getImages() == null ? new ArrayList<>() : new ArrayList<>(req.getImages()));

        // Highlight bullets — trim and drop blanks, preserving order.
        List<String> features = new ArrayList<>();
        if (req.getFeatures() != null) {
            for (String f : req.getFeatures()) {
                String t = f == null ? "" : f.trim();
                if (!t.isEmpty()) {
                    features.add(t);
                }
            }
        }
        p.setFeatures(features);

        reconcileVariants(p, req);
    }

    /** Match request variants to existing by id (updating), add new ones, drop the rest (orphanRemoval). */
    private void reconcileVariants(Product p, UpsertProductRequest req) {
        List<ProductVariant> result = new ArrayList<>();
        if (req.getVariants() != null) {
            for (UpsertProductRequest.VariantInput in : req.getVariants()) {
                if (in.getLabel() == null || in.getLabel().isBlank()) {
                    continue;
                }
                ProductVariant v = p.getVariants().stream()
                        .filter(existing -> existing.getId() != null && existing.getId().equals(in.getId()))
                        .findFirst()
                        .orElseGet(ProductVariant::new);
                boolean vtracked = Boolean.TRUE.equals(in.getStockTracked());
                v.setLabel(in.getLabel().trim());
                v.setSku(emptyToNull(in.getSku()));
                v.setPrice(in.getPrice());
                v.setStockTracked(vtracked);
                v.setStock(vtracked ? in.getStock() : null);
                if (v.getReserved() == null) {
                    v.setReserved(0);
                }
                result.add(v);
            }
        }
        p.getVariants().clear();
        p.getVariants().addAll(result);
    }

    /** Rebuild the product's per-branch stock rows from the request (only when stockPerBranch). */
    private void replaceBranchStock(Product p, UpsertProductRequest req) {
        branchStockRepository.deleteByProductId(p.getId());
        if (!p.isStockPerBranch() || req.getBranchStock() == null) {
            return;
        }
        for (UpsertProductRequest.BranchStockInput in : req.getBranchStock()) {
            if (in.getBranchId() == null) {
                continue;
            }
            branchStockRepository.save(BranchStock.builder()
                    .productId(p.getId())
                    .variantId(in.getVariantId())
                    .branchId(in.getBranchId())
                    .stock(in.getStock() == null ? 0 : in.getStock())
                    .reserved(0)
                    .build());
        }
    }

    private boolean offeredAtBranch(Product p, Long branchId) {
        if (branchId == null || p.getBranchScope() == BranchScope.GLOBAL) {
            return true;
        }
        return p.getAvailableBranchIds() != null && p.getAvailableBranchIds().contains(branchId);
    }

    private String resolveSlug(UpsertProductRequest req, Product existing) {
        if (req.getSlug() != null && !req.getSlug().isBlank()) {
            return slugify(req.getSlug());
        }
        if (existing != null && existing.getSlug() != null) {
            return existing.getSlug();
        }
        return slugify(req.getName());
    }

    private static String slugify(String raw) {
        String slug = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "item" : slug;
    }

    private static boolean truthy(String v) {
        String s = v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("1") || s.equals("yes");
    }

    private static String emptyToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private ProductDto toDto(Product p) {
        boolean sharedTracked = p.isStockTracked() && !p.isStockPerBranch();
        List<ProductVariantDto> variants = p.getVariants().stream()
                .map(v -> ProductVariantDto.builder()
                        .id(v.getId())
                        .label(v.getLabel())
                        .sku(v.getSku())
                        .price(v.getPrice())
                        .stockTracked(v.isStockTracked())
                        .stock(v.getStock())
                        .reserved(v.getReserved())
                        .available(v.isStockTracked() && !p.isStockPerBranch() ? available(v.getStock(), v.getReserved()) : null)
                        .build())
                .toList();
        return ProductDto.builder()
                .id(p.getId())
                .name(p.getName())
                .slug(p.getSlug())
                .description(p.getDescription())
                .price(p.getPrice())
                .branchScope(p.getBranchScope())
                .availableBranchIds(p.getAvailableBranchIds())
                .stockTracked(p.isStockTracked())
                .stockPerBranch(p.isStockPerBranch())
                .stock(p.getStock())
                .reserved(p.getReserved())
                .available(sharedTracked && p.getVariants().isEmpty() ? available(p.getStock(), p.getReserved()) : null)
                .variants(variants)
                .imageUrl(p.getImageUrl())
                .images(p.getImages())
                .features(p.getFeatures())
                .category(p.getCategory())
                .active(p.isActive())
                .workflowCode(p.getWorkflowCode())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private static Integer available(Integer stock, Integer reserved) {
        return (stock == null ? 0 : stock) - (reserved == null ? 0 : reserved);
    }
}
