package com.webappfoundation.service;

import com.webappfoundation.dto.GalleryItemDto;
import com.webappfoundation.dto.MediaAssetDto;
import com.webappfoundation.exception.ResourceNotFoundException;
import com.webappfoundation.model.GallerySection;
import com.webappfoundation.model.MediaAsset;
import com.webappfoundation.repository.MediaAssetRepository;
import com.webappfoundation.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * The single media library: upload an image once, get a URL, reference it anywhere (a Trainer's
 * photoUrl, a product image), and optionally publish it to the public gallery via showInGallery.
 * Metadata rows live in Postgres; bytes in object storage. The public gallery is a view over this.
 */
@Service
public class MediaAssetService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final MediaAssetRepository repository;
    private final StorageService storage;

    public MediaAssetService(MediaAssetRepository repository, StorageService storage) {
        this.repository = repository;
        this.storage = storage;
    }

    // ── Admin: media library ──

    public List<MediaAssetDto> list() {
        return repository.findAllByOrderByUploadedAtDesc().stream().map(this::toDto).toList();
    }

    public MediaAssetDto upload(MultipartFile file, String label, Boolean showInGallery,
                                GallerySection section, String eventName, LocalDate eventDate,
                                Integer sortOrder) {
        validate(file);
        MediaAsset asset = MediaAsset.builder()
                .objectKey(storage.store(file, "media"))
                .filename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .label(label)
                .uploadedAt(Instant.now())
                .build();
        applyGallery(asset, showInGallery, section, eventName, eventDate, sortOrder);
        return toDto(repository.save(asset));
    }

    /** Replace the image (optional new file) and/or edit metadata + gallery publishing. */
    public MediaAssetDto update(Long id, MultipartFile file, String label, Boolean showInGallery,
                                GallerySection section, String eventName, LocalDate eventDate,
                                Integer sortOrder) {
        MediaAsset asset = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset not found: " + id));
        if (file != null && !file.isEmpty()) {
            validate(file);
            String oldKey = asset.getObjectKey();
            asset.setObjectKey(storage.store(file, "media"));
            asset.setFilename(file.getOriginalFilename());
            asset.setContentType(file.getContentType());
            asset.setSizeBytes(file.getSize());
            storage.delete(oldKey);
        }
        if (label != null) {
            asset.setLabel(label);
        }
        applyGallery(asset, showInGallery, section, eventName, eventDate, sortOrder);
        return toDto(repository.save(asset));
    }

    public void delete(Long id) {
        MediaAsset asset = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset not found: " + id));
        storage.delete(asset.getObjectKey());
        repository.delete(asset);
    }

    // ── Public: gallery is a view over media flagged showInGallery ──

    public List<GalleryItemDto> listForGallery(GallerySection section) {
        List<MediaAsset> assets = (section == null)
                ? repository.findByShowInGalleryTrueOrderBySortOrderAscUploadedAtDesc()
                : repository.findByShowInGalleryTrueAndSectionOrderBySortOrderAscUploadedAtDesc(section);
        return assets.stream().map(this::toGalleryDto).toList();
    }

    // ── Helpers ──

    private void applyGallery(MediaAsset a, Boolean showInGallery, GallerySection section,
                              String eventName, LocalDate eventDate, Integer sortOrder) {
        boolean show = Boolean.TRUE.equals(showInGallery);
        a.setShowInGallery(show);
        a.setSortOrder(sortOrder == null ? 0 : sortOrder);
        if (show) {
            GallerySection resolved = section == null ? GallerySection.WEBSITE : section;
            a.setSection(resolved);
            a.setEventName(resolved == GallerySection.EVENT ? eventName : null);
            a.setEventDate(resolved == GallerySection.EVENT ? eventDate : null);
        } else {
            a.setSection(null);
            a.setEventName(null);
            a.setEventDate(null);
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An image file is required");
        }
        if (file.getContentType() == null || !ALLOWED_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported media type: " + file.getContentType());
        }
    }

    private MediaAssetDto toDto(MediaAsset a) {
        return MediaAssetDto.builder()
                .id(a.getId())
                .url("/api/v1/media/" + a.getObjectKey())
                .filename(a.getFilename())
                .contentType(a.getContentType())
                .sizeBytes(a.getSizeBytes())
                .label(a.getLabel())
                .showInGallery(Boolean.TRUE.equals(a.getShowInGallery()))
                .section(a.getSection())
                .eventName(a.getEventName())
                .eventDate(a.getEventDate())
                .sortOrder(a.getSortOrder())
                .uploadedAt(a.getUploadedAt())
                .build();
    }

    private GalleryItemDto toGalleryDto(MediaAsset a) {
        return GalleryItemDto.builder()
                .id(a.getId())
                .url("/api/v1/media/" + a.getObjectKey())
                .caption(a.getLabel())
                .section(a.getSection())
                .eventName(a.getEventName())
                .eventDate(a.getEventDate())
                .sortOrder(a.getSortOrder() == null ? 0 : a.getSortOrder())
                .build();
    }
}
