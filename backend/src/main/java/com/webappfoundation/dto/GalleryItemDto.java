package com.webappfoundation.dto;

import com.webappfoundation.model.GallerySection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** What the frontend consumes for a gallery item. {@code url} is a stable app path served by MediaController. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GalleryItemDto {
    private Long id;
    private String url;            // /api/v1/media/<objectKey>
    private String caption;
    private GallerySection section;
    private String eventName;
    private LocalDate eventDate;
    private Integer sortOrder;
}
