package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Create/update payload for the admin event form. {@code slug} is derived from {@code name} when blank.
 * {@code capacity} is the shared seat pool (null = unlimited); tiers, when present, carry their own seat
 * stock and make the event non-cartable (pick a tier on the detail page).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertEventRequest {
    private String name;
    private String slug;
    private String description;
    private BigDecimal price;
    private String category;
    private Boolean active;
    private String workflowCode;

    private Instant startsAt;
    private Instant endsAt;
    private Instant salesCloseAt;
    private Long venueBranchId;

    private Integer capacity;

    private String imageUrl;
    private List<String> images;
    private List<String> features;

    private List<TierInput> tiers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TierInput {
        private Long id;          // present on update of an existing tier
        private String label;
        private String sku;
        private BigDecimal price;
        private Integer stock;
    }
}
