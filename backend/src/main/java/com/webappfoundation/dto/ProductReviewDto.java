package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A single product review. {@code mine} marks the current caller's own review so the UI can offer edit/delete.
 * {@code productName} is populated only in the admin moderation list (null on the public detail page).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewDto {
    private Long id;
    private Long productId;
    private String productName;
    private Integer userId;
    private String userName;
    private int rating;
    private String comment;
    private Instant createdAt;
    private boolean mine;
}
