package com.webappfoundation.controller.admin;

import com.webappfoundation.dto.BulkUploadResult;
import com.webappfoundation.dto.EventDto;
import com.webappfoundation.dto.UpsertEventRequest;
import com.webappfoundation.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Admin event management. ADMIN-gated at the filter level by SecurityConfig ({@code /api/v1/admin/**}).
 * Writes are audited inside {@link EventService}. {@code GET} returns all events incl. inactive/past.
 */
@RestController
@RequestMapping("/api/v1/admin/events")
public class AdminEventController {

    private final EventService eventService;

    public AdminEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<EventDto> list() {
        return eventService.adminList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventDto create(@RequestBody UpsertEventRequest request) {
        return eventService.create(request);
    }

    @PutMapping("/{id}")
    public EventDto update(@PathVariable Long id, @RequestBody UpsertEventRequest request) {
        return eventService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        eventService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/bulk-upload", consumes = "multipart/form-data")
    public BulkUploadResult bulkUpload(@RequestParam("file") MultipartFile file) {
        return eventService.bulkImport(file);
    }
}
