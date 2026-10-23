package com.webappfoundation.controller.admin;

import com.webappfoundation.dto.BulkUploadResult;
import com.webappfoundation.dto.RecommendationDto;
import com.webappfoundation.dto.UpsertRecommendationRequest;
import com.webappfoundation.service.RecommendationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Admin recommendation management. ADMIN-gated by SecurityConfig ({@code /api/v1/admin/**}). Writes are
 * audited inside {@link RecommendationService}.
 */
@RestController
@RequestMapping("/api/v1/admin/recommendations")
public class AdminRecommendationController {

    private final RecommendationService recommendationService;

    public AdminRecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public List<RecommendationDto> list() {
        return recommendationService.all();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecommendationDto create(@RequestBody UpsertRecommendationRequest request) {
        return recommendationService.create(request);
    }

    @PutMapping("/{id}")
    public RecommendationDto update(@PathVariable Long id, @RequestBody UpsertRecommendationRequest request) {
        return recommendationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        recommendationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/bulk-upload", consumes = "multipart/form-data")
    public BulkUploadResult bulkUpload(@RequestParam("file") MultipartFile file) {
        return recommendationService.bulkImport(file);
    }
}
