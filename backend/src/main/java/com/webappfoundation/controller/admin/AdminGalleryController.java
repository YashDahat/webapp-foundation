package com.webappfoundation.controller.admin;

import com.webappfoundation.dto.GalleryItemDto;
import com.webappfoundation.dto.GalleryUpdateRequest;
import com.webappfoundation.model.GallerySection;
import com.webappfoundation.service.GalleryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * Admin gallery management. Under /api/v1/admin/** so SecurityConfig's hasAuthority("ADMIN") guards it.
 * Upload is multipart (the image file + metadata); edit/delete operate on the Long id.
 */
@RestController
@RequestMapping("/api/v1/admin/gallery")
public class AdminGalleryController {

    private final GalleryService galleryService;

    public AdminGalleryController(GalleryService galleryService) {
        this.galleryService = galleryService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<GalleryItemDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String caption,
            @RequestParam(required = false) GallerySection section,
            @RequestParam(required = false) String eventName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate) {
        GalleryItemDto created = galleryService.upload(file, caption, section, eventName, eventDate);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public GalleryItemDto update(@PathVariable Long id, @RequestBody GalleryUpdateRequest request) {
        return galleryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        galleryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
