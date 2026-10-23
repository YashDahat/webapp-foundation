package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The universal "sellable" exchange shape shared by the cart, recommendations, and orders.
 * Any sellable type — a default {@code Product}, a {@code Room}, a {@code Membership}, a dish —
 * maps to this contract via its {@link com.webappfoundation.catalog.CatalogItemResolver}, so the
 * engines stay business-agnostic and reference items only by {@code (itemType, itemId)}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogItemDto {
    private String itemType;              // business-defined key, e.g. "PRODUCT", "ROOM", "MEMBERSHIP"
    private Long itemId;                  // id within that type's own table
    private String name;
    private String description;
    private BigDecimal price;
    private String priceUnit;             // optional, e.g. "per night", "per session"
    private String imageUrl;              // thumbnail / primary (media-library URL)
    private List<String> images;          // gallery (media-library URLs)
    private String slug;
    private String detailPath;            // front-end route to the item's detail page, e.g. "/products/<slug>"
    private String category;
    private boolean active;
    private boolean cartable;             // one-click "Add" (true) vs. needs configuration first (false)
    private String workflowCode;          // the order lifecycle (+ cancellation/refund policy) this item follows
    private Map<String, Object> attributes; // small free-form long-tail only — NOT the primary model
}
