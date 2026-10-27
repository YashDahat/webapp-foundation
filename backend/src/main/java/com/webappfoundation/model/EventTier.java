package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A ticket tier for an {@link Event} (e.g. "Early bird", "General", "VIP"), mirroring {@code ProductVariant}.
 * Each tier owns its own seat pool ({@code stock}/{@code reserved}, {@code available = stock - reserved}).
 * When an event has tiers it is {@code cartable=false} — the buyer picks a tier on the detail page.
 * {@code price} null → inherit the event's base price.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "event_tier")
public class EventTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The tier label, e.g. "Early bird" / "General" / "VIP". */
    @Column(nullable = false)
    private String label;

    private String sku;

    /** Null → inherit the owning event's base price. */
    private BigDecimal price;

    /** Seats allocated to this tier. */
    private Integer stock;

    @Builder.Default
    private Integer reserved = 0;
}
