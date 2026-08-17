package com.webappfoundation.controller;

import com.webappfoundation.dto.GalleryItemDto;
import com.webappfoundation.model.GallerySection;
import com.webappfoundation.service.MediaAssetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public, read-only gallery — a view over the media library (images flagged showInGallery). Optionally
 * filter by section (?section=WEBSITE|EVENT). Admins publish images to the gallery from /admin/media.
 */
@RestController
@RequestMapping("/api/v1/gallery")
public class GalleryController {

    private final MediaAssetService mediaAssetService;

    public GalleryController(MediaAssetService mediaAssetService) {
        this.mediaAssetService = mediaAssetService;
    }

    @GetMapping
    public List<GalleryItemDto> list(@RequestParam(required = false) GallerySection section) {
        return mediaAssetService.listForGallery(section);
    }
}
