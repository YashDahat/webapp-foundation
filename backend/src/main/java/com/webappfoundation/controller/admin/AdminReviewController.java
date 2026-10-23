package com.webappfoundation.controller.admin;

import com.webappfoundation.dto.ProductReviewDto;
import com.webappfoundation.service.ProductReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin review moderation. ADMIN-gated by SecurityConfig ({@code /api/v1/admin/**}). Read-only list + delete
 * (audited inside {@link ProductReviewService}); user-generated content → no bulk-upload.
 */
@RestController
@RequestMapping("/api/v1/admin/reviews")
public class AdminReviewController {

    private final ProductReviewService reviewService;

    public AdminReviewController(ProductReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public List<ProductReviewDto> list() {
        return reviewService.adminList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reviewService.adminDelete(id);
        return ResponseEntity.noContent().build();
    }
}
