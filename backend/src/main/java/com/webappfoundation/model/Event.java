package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A dated, finite-capacity sellable (workshop, class, gig, tasting night). An event is essentially a
 * {@link Product} with a date and a seat count: it maps to the same {@code CatalogItemDto} contract via
 * {@code EventCatalogResolver} ({@code itemType="EVENT"}) and its seats are tracked through the fenced
 * {@code InventoryService} ({@code EventInventoryProvider}) — so registration/ticketing runs on the normal
 * order spine with no new engine.
 *
 * <p>Capacity model (mirrors {@link Product}'s shared-pool + variants): a variant-less event uses the
 * shared {@code capacity}/{@code reserved} pool ({@code capacity == null} = unlimited); a tiered event
 * (GA / VIP / Early-bird) tracks seats <b>per {@link EventTier}</b> and is non-cartable (pick a tier on the
 * detail page). Sales stop at {@code salesCloseAt} (defaults to {@code startsAt} when null).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "event")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    // ── Schedule ──

    @Column(nullable = false)
    private Instant startsAt;

    private Instant endsAt;

    /** Sales stop at this instant; null → defaults to {@link #startsAt}. */
    private Instant salesCloseAt;

    /** Venue outlet the event runs at (also the order's branch); null = unscoped. */
    private Long venueBranchId;

    // ── Capacity (shared pool; null = unlimited). Tiered events track seats per {@link EventTier}. ──

    private Integer capacity;

    @Builder.Default
    private Integer reserved = 0;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id")
    @OrderColumn(name = "tier_order")
    @Builder.Default
    private List<EventTier> tiers = new ArrayList<>();

    // ── Media (media-library URLs only) ──

    private String imageUrl;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_images", joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "image_order")
    @Column(name = "image_url")
    @Builder.Default
    private List<String> images = new ArrayList<>();

    private String category;

    /** Short highlight bullets shown on the event detail page. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_features", joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "feature_order")
    @Column(name = "feature", columnDefinition = "text")
    @Builder.Default
    private List<String> features = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Order lifecycle this event follows (blank = the foundation default workflow). */
    private String workflowCode;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
