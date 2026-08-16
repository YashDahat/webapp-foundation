package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One image/video in the site gallery. Bytes live in object storage (keyed by {@code objectKey});
 * this row is the metadata. Primary key is a Long (foundation id rule — never UUID).
 */
@Entity
@Table(name = "gallery_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GalleryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Opaque storage key in the object store. */
    @Column(nullable = false)
    private String objectKey;

    /** MIME type, used when serving the object. */
    @Column(nullable = false)
    private String contentType;

    @Column(length = 500)
    private String caption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GallerySection section;

    /** Set when section = EVENT — groups images into an album by name. */
    private String eventName;

    /** Set when section = EVENT. */
    private LocalDate eventDate;

    /** Manual ordering within a section/event (ascending). */
    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt;
}
