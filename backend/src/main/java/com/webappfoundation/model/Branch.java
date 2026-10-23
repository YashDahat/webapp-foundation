package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A physical outlet an order is fulfilled from (or picked up at). Each product declares its branch
 * availability ({@code GLOBAL} or {@code SELECTED} branches); the selected branch filters the catalog +
 * recommendations and is recorded on the order. Coordinates drive the "nearest branch" picker.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "branch")
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private String phone;
    private String addressLine;
    private String city;
    private String state;
    private String pincode;
    private Double latitude;
    private Double longitude;
    /** A shareable Google Maps link for the outlet (e.g. a "share this place" URL), shown as "View on Google Maps". */
    @Column(columnDefinition = "text")
    private String googleMapsUrl;
    private String openingHours;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
