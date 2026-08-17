package com.webappfoundation.dto;

import com.webappfoundation.model.GallerySection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/** A media-library entry. {@code url} is ready to use in an <img src> or to store on a domain entity. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaAssetDto {
    private Long id;
    private String url;          // /api/v1/media/<objectKey>
    private String filename;
    private String contentType;
    private Long sizeBytes;
    private String label;
    private Boolean showInGallery;
    private GallerySection section;
    private String eventName;
    private LocalDate eventDate;
    private Integer sortOrder;
    private Instant uploadedAt;
}
