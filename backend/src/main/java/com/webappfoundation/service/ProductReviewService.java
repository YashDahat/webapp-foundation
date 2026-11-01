package com.webappfoundation.service;

import com.webappfoundation.dto.CreateProductReviewRequest;
import com.webappfoundation.dto.ProductReviewDto;
import com.webappfoundation.dto.ProductReviewSummaryDto;
import com.webappfoundation.exception.ResourceNotFoundException;
import com.webappfoundation.model.LoyaltyEventType;
import com.webappfoundation.model.Product;
import com.webappfoundation.model.ProductReview;
import com.webappfoundation.model.User;
import com.webappfoundation.repository.ProductRepository;
import com.webappfoundation.repository.ProductReviewRepository;
import com.webappfoundation.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Product reviews: any signed-in user may leave ONE review per product (submit is an upsert — editing their
 * own), delete their own, and read the public summary (aggregate stars + newest-first list). Admins read and
 * moderate (delete) at {@code /admin/reviews}. Writes are audited; the comment is user-generated and not
 * treated as sensitive.
 */
@Service
public class ProductReviewService {

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final LoyaltyEventService loyaltyEventService;

    public ProductReviewService(ProductReviewRepository reviewRepository, ProductRepository productRepository,
                                UserRepository userRepository, AuditService auditService,
                                LoyaltyEventService loyaltyEventService) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.loyaltyEventService = loyaltyEventService;
    }

    // ── Public ──

    /** Aggregate + list of reviews for a product; {@code userId} (nullable) marks the caller's own review. */
    public ProductReviewSummaryDto summary(Long productId, Integer userId) {
        requireProduct(productId);
        List<ProductReview> rows = reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            distribution.put(star, 0L);
        }
        long sum = 0;
        for (ProductReview r : rows) {
            distribution.merge(r.getRating(), 1L, Long::sum);
            sum += r.getRating();
        }
        double average = rows.isEmpty() ? 0.0 : Math.round((sum * 10.0) / rows.size()) / 10.0;

        List<ProductReviewDto> dtos = rows.stream().map(r -> toDto(r, userId, null)).toList();
        ProductReviewDto mine = dtos.stream().filter(ProductReviewDto::isMine).findFirst().orElse(null);

        return ProductReviewSummaryDto.builder()
                .average(average)
                .count(rows.size())
                .distribution(distribution)
                .reviews(dtos)
                .myReview(mine)
                .build();
    }

    @Transactional
    public ProductReviewDto submit(Integer userId, Long productId, CreateProductReviewRequest req) {
        if (req == null || req.getRating() < 1 || req.getRating() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating must be between 1 and 5");
        }
        requireProduct(productId);
        String userName = displayName(userId);
        Instant now = Instant.now();

        ProductReview existing = reviewRepository.findByProductIdAndUserId(productId, userId).orElse(null);
        boolean firstReview = existing == null;
        ProductReview review = existing != null ? existing
                : ProductReview.builder().productId(productId).userId(userId).createdAt(now).build();
        review.setUserName(userName);
        review.setRating(req.getRating());
        review.setComment(trimToNull(req.getComment()));
        review.setUpdatedAt(now);
        ProductReview saved = reviewRepository.save(review);

        auditService.record(AuditActions.REVIEW_SUBMITTED, AuditActions.SUCCESS, "PRODUCT",
                String.valueOf(productId), "rating=" + saved.getRating());
        // Loyalty: award review points once per product (upserts/edits don't re-earn). Best-effort — never breaks the review.
        if (firstReview) {
            loyaltyEventService.record(userId, LoyaltyEventType.REVIEW, null, Map.of("productId", productId));
        }
        return toDto(saved, userId, null);
    }

    @Transactional
    public void deleteOwn(Integer userId, Long productId) {
        ProductReview review = reviewRepository.findByProductIdAndUserId(productId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("You have not reviewed this product"));
        reviewRepository.delete(review);
        auditService.record(AuditActions.REVIEW_DELETED, AuditActions.SUCCESS, "PRODUCT",
                String.valueOf(productId), "self");
    }

    // ── Admin ──

    public List<ProductReviewDto> adminList() {
        List<ProductReview> rows = reviewRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, String> productNames = productRepository.findAllById(
                        rows.stream().map(ProductReview::getProductId).toList()).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));
        return rows.stream().map(r -> toDto(r, null, productNames.get(r.getProductId()))).toList();
    }

    @Transactional
    public void adminDelete(Long id) {
        ProductReview review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + id));
        reviewRepository.delete(review);
        auditService.record(AuditActions.REVIEW_DELETED, AuditActions.SUCCESS, "PRODUCT",
                String.valueOf(review.getProductId()), "reviewId=" + id);
    }

    // ── Helpers ──

    private void requireProduct(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
    }

    /** A friendly display name for the reviewer; falls back to the email local-part, then "Customer". */
    private String displayName(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue"));
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        if (!name.isEmpty()) {
            return name;
        }
        if (user.getEmail() != null && user.getEmail().contains("@")) {
            return user.getEmail().substring(0, user.getEmail().indexOf('@'));
        }
        return "Customer";
    }

    private ProductReviewDto toDto(ProductReview r, Integer currentUserId, String productName) {
        return ProductReviewDto.builder()
                .id(r.getId())
                .productId(r.getProductId())
                .productName(productName)
                .userId(r.getUserId())
                .userName(r.getUserName())
                .rating(r.getRating())
                .comment(r.getComment())
                .createdAt(r.getCreatedAt())
                .mine(currentUserId != null && currentUserId.equals(r.getUserId()))
                .build();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
