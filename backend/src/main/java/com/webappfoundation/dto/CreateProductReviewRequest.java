package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Create/update payload for a product review. {@code rating} is 1–5; {@code comment} is optional. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductReviewRequest {
    private int rating;
    private String comment;
}
