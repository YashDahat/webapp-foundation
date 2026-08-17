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
 * A generic uploaded image in the media library — the ONE place images are managed. Any domain entity
 * can reference one by storing its URL (e.g. a Trainer's photoUrl). Optionally, an image can be
 * published to the public gallery via {@code showInGallery} (+ section / event metadata) — the gallery
 * is a curated view over the media library, not a separate store. Bytes live in object storage.
 */
@Entity
@Table(name = "media_asset")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Opaque object-storage key. */
    @Column(nullable = false)
    private String objectKey;

    /** Original upload filename, for display. */
    private String filename;

    @Column(nullable = false)
    private String contentType;

    private Long sizeBytes;

    /** Optional human label / alt text (also used as the public gallery caption). */
    @Column(length = 500)
    private String label;

    // ── Public gallery (a curated view over the media library) ──
    /** Whether this image is published to the public gallery. Null = false. */
    private Boolean showInGallery;

    /** WEBSITE (main gallery) or EVENT (grouped by name/date). Relevant only when showInGallery. */
    @Enumerated(EnumType.STRING)
    private GallerySection section;

    private String eventName;
    private LocalDate eventDate;

    /** Display order within the public gallery (ascending). */
    private Integer sortOrder;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt;
}
