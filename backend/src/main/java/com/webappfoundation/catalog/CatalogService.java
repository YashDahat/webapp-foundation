package com.webappfoundation.catalog;

import com.webappfoundation.dto.CatalogItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The business-agnostic entry point into the catalog. Collects every {@link CatalogItemResolver}
 * bean into a {@code Map<itemType, resolver>} and resolves {@code (itemType, itemId)} references to
 * {@link CatalogItemDto}s. The cart, recommendation, and order spines call this instead of knowing
 * about any concrete sellable type. An unknown {@code itemType} is logged and skipped — never thrown.
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final Map<String, CatalogItemResolver> resolvers;

    public CatalogService(List<CatalogItemResolver> resolverBeans) {
        this.resolvers = resolverBeans.stream()
                .collect(Collectors.toMap(CatalogItemResolver::itemType, Function.identity()));
    }

    /** Is there a resolver registered for this itemType? */
    public boolean supports(String itemType) {
        return itemType != null && resolvers.containsKey(itemType);
    }

    /** Resolve ids of a single type. Unknown type or empty ids → empty list (unknown is logged). */
    public List<CatalogItemDto> resolve(String itemType, Collection<Long> ids) {
        if (itemType == null || ids == null || ids.isEmpty()) {
            return List.of();
        }
        CatalogItemResolver resolver = resolvers.get(itemType);
        if (resolver == null) {
            log.warn("No CatalogItemResolver registered for itemType '{}' — skipping {} id(s)", itemType, ids.size());
            return List.of();
        }
        return resolver.resolve(ids);
    }

    /** Resolve a single reference, or {@code null} if no active item matches. */
    public CatalogItemDto resolveOne(String itemType, Long itemId) {
        if (itemId == null) {
            return null;
        }
        return resolve(itemType, List.of(itemId)).stream().findFirst().orElse(null);
    }

    /**
     * Resolve a mix of references of different types: groups the refs by {@code itemType}, fans each
     * group out to its resolver, and flattens the results. Unknown types are skipped (logged).
     */
    public List<CatalogItemDto> resolveMixed(Collection<ItemRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        Map<String, List<Long>> idsByType = new HashMap<>();
        for (ItemRef ref : refs) {
            if (ref != null && ref.itemType() != null && ref.itemId() != null) {
                idsByType.computeIfAbsent(ref.itemType(), k -> new ArrayList<>()).add(ref.itemId());
            }
        }
        List<CatalogItemDto> out = new ArrayList<>();
        idsByType.forEach((type, ids) -> out.addAll(resolve(type, ids)));
        return out;
    }

    /** A polymorphic catalog reference, shared by recommendations and orders. */
    public record ItemRef(String itemType, Long itemId) {}
}
