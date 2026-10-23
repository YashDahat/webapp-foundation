package com.webappfoundation.inventory;

import com.webappfoundation.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Business-agnostic entry point into inventory. Collects every {@link InventoryProvider} bean keyed by
 * {@code itemType} (like {@code CatalogService}) and fans availability/reservation calls to the matching
 * provider. A type with no provider is treated as unlimited (no-op reserve/commit/release, always
 * available) so sellable types without stock semantics work out of the box.
 *
 * <p>The batch {@code *All(List&lt;OrderItem&gt;, branchId)} overloads and the reservation-TTL sweep are
 * added with the order spine (they reference the order model).
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final Map<String, InventoryProvider> providers;

    public InventoryService(List<InventoryProvider> providerBeans) {
        this.providers = providerBeans.stream()
                .collect(Collectors.toMap(InventoryProvider::itemType, Function.identity()));
    }

    /** Is the item offered at this branch? Unknown type → true (no provider = unlimited/unscoped). */
    public boolean availableAt(String itemType, Long itemId, Long branchId) {
        InventoryProvider provider = providers.get(itemType);
        return provider == null || provider.availableAt(itemId, branchId);
    }

    /** Atomically hold {@code qty} units; a shortfall throws 409. No-op when no provider handles the type. */
    public void reserve(String itemType, Long itemId, String variantKey, Long branchId, int qty) {
        InventoryProvider provider = providers.get(itemType);
        if (provider == null) {
            log.debug("No InventoryProvider for itemType '{}' — reserve is a no-op (unlimited)", itemType);
            return;
        }
        provider.reserve(itemId, variantKey, branchId, qty);
    }

    /** Convert a reservation to sold. No-op when no provider handles the type. */
    public void commit(String itemType, Long itemId, String variantKey, Long branchId, int qty) {
        InventoryProvider provider = providers.get(itemType);
        if (provider != null) {
            provider.commit(itemId, variantKey, branchId, qty);
        }
    }

    /** Give back held/sold units. No-op when no provider handles the type. */
    public void release(String itemType, Long itemId, String variantKey, Long branchId, int qty) {
        InventoryProvider provider = providers.get(itemType);
        if (provider != null) {
            provider.release(itemId, variantKey, branchId, qty);
        }
    }

    // ── Batch overloads over an order's lines (used by the order spine) ──

    /** True only if every line is available at the branch (gates placement before reserving). */
    public boolean availableAll(List<OrderItem> items, Long branchId) {
        return items.stream().allMatch(i -> availableAt(i.getItemType(), i.getItemId(), branchId));
    }

    /** Reserve every line; a shortfall throws 409 and (within the caller's transaction) rolls back prior holds. */
    public void reserveAll(List<OrderItem> items, Long branchId) {
        for (OrderItem i : items) {
            reserve(i.getItemType(), i.getItemId(), i.getVariantKey(), branchId, i.getQuantity());
        }
    }

    /** Convert every line's reservation to sold (on payment capture). */
    public void commitAll(List<OrderItem> items, Long branchId) {
        for (OrderItem i : items) {
            commit(i.getItemType(), i.getItemId(), i.getVariantKey(), branchId, i.getQuantity());
        }
    }

    /** Give back every line's held/sold units (cancel / expiry / return). */
    public void releaseAll(List<OrderItem> items, Long branchId) {
        for (OrderItem i : items) {
            release(i.getItemType(), i.getItemId(), i.getVariantKey(), branchId, i.getQuantity());
        }
    }
}
