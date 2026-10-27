package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Admin/detail shape for an {@link com.webappfoundation.model.Event}. {@code available} = seats left on the
 * shared pool ({@code capacity - reserved}; null = unlimited or tiered — read per-tier then). {@code onSale}
 * / {@code soldOut} are computed from the sales window + remaining seats so the UI can gate the sign-up form.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventDto {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private BigDecimal price;

    private Instant startsAt;
    private Instant endsAt;
    private Instant salesCloseAt;

    private Long venueBranchId;
    private String venueName;

    private Integer capacity;
    private Integer reserved;
    private Integer available;   // seats left on the shared pool; null when unlimited or tiered

    /** Sum of remaining seats across the event (pool or all tiers); null when unlimited. */
    private Integer seatsLeft;
    private boolean onSale;      // active && now < salesCloseAt && seats remain
    private boolean soldOut;     // active && no seats remain

    private List<EventTierDto> tiers;

    private String imageUrl;
    private List<String> images;
    private List<String> features;
    private String category;
    private boolean active;
    private String workflowCode;

    private Instant createdAt;
    private Instant updatedAt;
}
