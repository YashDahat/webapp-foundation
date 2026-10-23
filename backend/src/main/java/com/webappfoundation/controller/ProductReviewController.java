package com.webappfoundation.controller;

import com.webappfoundation.dto.CreateProductReviewRequest;
import com.webappfoundation.dto.ProductReviewDto;
import com.webappfoundation.dto.ProductReviewSummaryDto;
import com.webappfoundation.security.CurrentUser;
import com.webappfoundation.service.ProductReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Product review endpoints. Reading the summary is public (identity is optional — {@code @CurrentUser} is null
 * for a guest, and only marks the caller's own review). Writing/deleting requires a signed-in user. Reachable
 * paths are permitted by SecurityConfig, so write methods reject anonymous callers with 401.
 */
@RestController
@RequestMapping("/api/v1/products/{productId}/reviews")
public class ProductReviewController {

    private final ProductReviewService reviewService;

    public ProductReviewController(ProductReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ProductReviewSummaryDto list(@CurrentUser Integer userId, @PathVariable Long productId) {
        return reviewService.summary(productId, userId);
    }

    @PostMapping
    public ProductReviewDto submit(@CurrentUser Integer userId, @PathVariable Long productId,
                                   @RequestBody CreateProductReviewRequest request) {
        return reviewService.submit(requireUser(userId), productId, request);
    }

    @DeleteMapping("/mine")
    public ResponseEntity<Void> deleteOwn(@CurrentUser Integer userId, @PathVariable Long productId) {
        reviewService.deleteOwn(requireUser(userId), productId);
        return ResponseEntity.noContent().build();
    }

    private Integer requireUser(Integer userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to leave a review");
        }
        return userId;
    }
}
