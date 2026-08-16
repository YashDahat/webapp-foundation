package com.webappfoundation.controller;

import com.webappfoundation.dto.GalleryItemDto;
import com.webappfoundation.model.GallerySection;
import com.webappfoundation.service.GalleryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public, read-only gallery listing. Optionally filter by section (?section=WEBSITE|EVENT). */
@RestController
@RequestMapping("/api/v1/gallery")
public class GalleryController {

    private final GalleryService galleryService;

    public GalleryController(GalleryService galleryService) {
        this.galleryService = galleryService;
    }

    @GetMapping
    public List<GalleryItemDto> list(@RequestParam(required = false) GallerySection section) {
        return section == null ? galleryService.list() : galleryService.listBySection(section);
    }
}
