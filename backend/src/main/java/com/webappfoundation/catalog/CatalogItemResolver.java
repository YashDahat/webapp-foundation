package com.webappfoundation.catalog;

import com.webappfoundation.dto.CatalogItemDto;

import java.util.Collection;
import java.util.List;

/**
 * Per-business adapter that maps a sellable type's own entity to the universal {@link CatalogItemDto}
 * contract. A new sellable type ({@code Room}, {@code Membership}, …) ships one {@code @Component}
 * resolver alongside its typed entity — the cart, recommendation, and order engines bind to this
 * interface, so adding a type needs <b>zero</b> engine changes.
 */
public interface CatalogItemResolver {

    /** The {@code itemType} key this resolver handles (e.g. {@code "PRODUCT"}). */
    String itemType();

    /** Batch id -&gt; dto for active items only; ids with no active item are simply omitted. */
    List<CatalogItemDto> resolve(Collection<Long> ids);
}
