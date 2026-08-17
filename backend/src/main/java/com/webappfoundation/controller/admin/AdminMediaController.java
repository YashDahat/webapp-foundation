package com.webappfoundation.controller.admin;

import com.webappfoundation.dto.MediaAssetDto;
import com.webappfoundation.model.GallerySection;
import com.webappfoundation.service.MediaAssetService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * Generic media library — upload an image once, get a URL, reference it from any domain entity
 * (e.g. a Trainer's photoUrl). Under /api/v1/admin/** so SecurityConfig's hasAuthority("ADMIN") guards
 * it. Serving is public via GET /api/v1/media/{key} (MediaController).
 */
@RestController
@RequestMapping("/api/v1/admin/media")
public class AdminMediaController {

    private final MediaAssetService mediaAssetService;

    public AdminMediaController(MediaAssetService mediaAssetService) {
        this.mediaAssetService = mediaAssetService;
    }

    @GetMapping
    public List<MediaAssetDto> list() {
        return mediaAssetService.list();
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<MediaAssetDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String label,
            @RequestParam(required = false) Boolean showInGallery,
            @RequestParam(required = false) GallerySection section,
            @RequestParam(required = false) String eventName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
            @RequestParam(required = false) Integer sortOrder) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mediaAssetService.upload(file, label, showInGallery, section, eventName, eventDate, sortOrder));
    }

    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public MediaAssetDto update(
            @PathVariable Long id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(required = false) String label,
            @RequestParam(required = false) Boolean showInGallery,
            @RequestParam(required = false) GallerySection section,
            @RequestParam(required = false) String eventName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate,
            @RequestParam(required = false) Integer sortOrder) {
        return mediaAssetService.update(id, file, label, showInGallery, section, eventName, eventDate, sortOrder);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        mediaAssetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
