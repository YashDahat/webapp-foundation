package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A directed recommendation from one sellable to another, both referenced <b>polymorphically</b> by
 * {@code (itemType, itemId)} — so the engine is business-agnostic and a new sellable type needs zero
 * engine changes (just a {@code CatalogItemResolver}). {@code score} orders the results (desc).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "catalog_recommendation",
        uniqueConstraints = @UniqueConstraint(columnNames = {
                "sourceItemType", "sourceItemId", "targetItemType", "targetItemId", "relationType"}))
public class CatalogRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sourceItemType;

    @Column(nullable = false)
    private Long sourceItemId;

    @Column(nullable = false)
    private String targetItemType;

    @Column(nullable = false)
    private Long targetItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecommendationType relationType;

    @Builder.Default
    private Double score = 0.0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
