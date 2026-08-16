package com.webappfoundation.service;

import com.webappfoundation.dto.GalleryItemDto;
import com.webappfoundation.dto.GalleryUpdateRequest;
import com.webappfoundation.exception.ResourceNotFoundException;
import com.webappfoundation.model.GalleryItem;
import com.webappfoundation.model.GallerySection;
import com.webappfoundation.repository.GalleryItemRepository;
import com.webappfoundation.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Orchestrates gallery metadata (Postgres) + image bytes (object storage). */
@Service
public class GalleryService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4");

    private final GalleryItemRepository repository;
    private final StorageService storage;

    public GalleryService(GalleryItemRepository repository, StorageService storage) {
        this.repository = repository;
        this.storage = storage;
    }

    public List<GalleryItemDto> list() {
        return repository.findAllByOrderBySortOrderAscUploadedAtDesc().stream().map(this::toDto).toList();
    }

    public List<GalleryItemDto> listBySection(GallerySection section) {
        return repository.findBySectionOrderBySortOrderAscUploadedAtDesc(section).stream().map(this::toDto).toList();
    }

    public GalleryItemDto upload(MultipartFile file, String caption, GallerySection section,
                                 String eventName, LocalDate eventDate) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An image file is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported media type: " + contentType);
        }
        String key = storage.store(file, "gallery");
        GalleryItem item = GalleryItem.builder()
                .objectKey(key)
                .contentType(contentType)
                .caption(caption)
                .section(section == null ? GallerySection.WEBSITE : section)
                .eventName(eventName)
                .eventDate(eventDate)
                .sortOrder(0)
                .uploadedAt(Instant.now())
                .build();
        return toDto(repository.save(item));
    }

    public GalleryItemDto update(Long id, GalleryUpdateRequest req) {
        GalleryItem item = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gallery item not found: " + id));
        if (req.caption() != null) item.setCaption(req.caption());
        if (req.section() != null) item.setSection(req.section());
        item.setEventName(req.eventName());
        item.setEventDate(req.eventDate());
        if (req.sortOrder() != null) item.setSortOrder(req.sortOrder());
        return toDto(repository.save(item));
    }

    public void delete(Long id) {
        GalleryItem item = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gallery item not found: " + id));
        storage.delete(item.getObjectKey());
        repository.delete(item);
    }

    private GalleryItemDto toDto(GalleryItem i) {
        return GalleryItemDto.builder()
                .id(i.getId())
                .url("/api/v1/media/" + i.getObjectKey())
                .caption(i.getCaption())
                .section(i.getSection())
                .eventName(i.getEventName())
                .eventDate(i.getEventDate())
                .sortOrder(i.getSortOrder())
                .build();
    }
}
