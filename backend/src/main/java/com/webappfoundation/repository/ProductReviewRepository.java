package com.webappfoundation.repository;

import com.webappfoundation.model.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    List<ProductReview> findByProductIdOrderByCreatedAtDesc(Long productId);
    Optional<ProductReview> findByProductIdAndUserId(Long productId, Integer userId);
    List<ProductReview> findAllByOrderByCreatedAtDesc();
}
