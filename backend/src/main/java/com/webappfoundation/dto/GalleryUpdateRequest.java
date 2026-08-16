package com.webappfoundation.dto;

import com.webappfoundation.model.GallerySection;

import java.time.LocalDate;

/** Editable metadata for an existing gallery item (the image bytes are not changed here). */
public record GalleryUpdateRequest(
        String caption,
        GallerySection section,
        String eventName,
        LocalDate eventDate,
        Integer sortOrder) {}
