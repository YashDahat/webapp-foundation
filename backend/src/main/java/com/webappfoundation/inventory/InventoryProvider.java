package com.webappfoundation.inventory;

/**
 * Per-sellable-type availability + reservation, mirroring {@code CatalogItemResolver}: each type owns its
 * own stock semantics (products count units; a future room provider would check dates). Unlimited items are
 * a no-op, so food-style catalogs pay nothing for it. A new sellable type ships one provider alongside its
 * resolver — the order flow stays business-agnostic.
 */
public interface InventoryProvider {

    /** The {@code itemType} key this provider handles (e.g. {@code "PRODUCT"}). */
    String itemType();

    /** Is the item offered at this branch? ({@code branchId} null = ignore branch scoping.) */
    boolean availableAt(Long itemId, Long branchId);

    /** Atomically hold {@code qty} units; a shortfall throws 409 (ResponseStatusException CONFLICT). */
    void reserve(Long itemId, String variantKey, Long branchId, int qty);

    /** Convert a reservation to sold (on payment capture). */
    void commit(Long itemId, String variantKey, Long branchId, int qty);

    /** Give back held/sold units (cancel / expiry / return). */
    void release(Long itemId, String variantKey, Long branchId, int qty);
}
