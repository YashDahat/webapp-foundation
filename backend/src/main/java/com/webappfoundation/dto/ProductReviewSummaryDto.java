package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * The public reviews payload for a product detail page: aggregate {@code average}/{@code count}, a
 * {@code distribution} map (star 1–5 → count), the {@code reviews} list (newest first), and the caller's own
 * {@code myReview} (null for guests or users who haven't reviewed yet) so the UI can prefill the edit form.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewSummaryDto {
    private double average;
    private long count;
    private Map<Integer, Long> distribution;
    private List<ProductReviewDto> reviews;
    private ProductReviewDto myReview;
}
