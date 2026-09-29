package com.webappfoundation.controller;

import com.webappfoundation.dto.CreateSupportCaseRequest;
import com.webappfoundation.dto.MediaAssetDto;
import com.webappfoundation.dto.PostMessageRequest;
import com.webappfoundation.dto.SupportCaseDto;
import com.webappfoundation.dto.SupportThreadDto;
import com.webappfoundation.security.CurrentUser;
import com.webappfoundation.service.MediaAssetService;
import com.webappfoundation.service.SupportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Customer-facing support: open a case, list your own cases, read a thread, and reply. Requires a
 * logged-in user ({@code /api/v1/support} is permitAll at the filter level, so the current user is
 * enforced here via {@code @CurrentUser}).
 */
@RestController
@RequestMapping("/api/v1/support")
public class SupportController {

    private final SupportService supportService;
    private final MediaAssetService mediaAssetService;

    public SupportController(SupportService supportService, MediaAssetService mediaAssetService) {
        this.supportService = supportService;
        this.mediaAssetService = mediaAssetService;
    }

    @PostMapping
    public ResponseEntity<SupportCaseDto> open(@CurrentUser Integer userId,
                                               @RequestBody CreateSupportCaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supportService.open(requireUser(userId), request));
    }

    /**
     * Upload an image attachment for a support message. Unlike /api/v1/admin/media this is open to any
     * logged-in user (customers included) so they can attach images to a ticket; it stores into the same
     * media library and returns the asset (use its {@code url} in the message's {@code attachments}).
     */
    @PostMapping(value = "/attachments", consumes = "multipart/form-data")
    public ResponseEntity<MediaAssetDto> uploadAttachment(@CurrentUser Integer userId,
                                                          @RequestParam("file") MultipartFile file) {
        requireUser(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mediaAssetService.upload(file, "support attachment", false, null, null, null, null));
    }

    @GetMapping
    public List<SupportCaseDto> myCases(@CurrentUser Integer userId) {
        return supportService.myCases(requireUser(userId));
    }

    @GetMapping("/{id}")
    public SupportThreadDto thread(@CurrentUser Integer userId, @PathVariable Long id) {
        return supportService.thread(requireUser(userId), id);
    }

    @PostMapping("/{id}/messages")
    public SupportCaseDto reply(@CurrentUser Integer userId, @PathVariable Long id,
                                @RequestBody PostMessageRequest request) {
        return supportService.reply(requireUser(userId), id, request);
    }

    private Integer requireUser(Integer userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in to use support.");
        }
        return userId;
    }
}
