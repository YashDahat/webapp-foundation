package com.webappfoundation.dto;

import com.webappfoundation.model.RecommendationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Create/update payload for a recommendation. {@code itemType} defaults to "PRODUCT" on the admin form. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertRecommendationRequest {
    private String sourceItemType;
    private Long sourceItemId;
    private String targetItemType;
    private Long targetItemId;
    private RecommendationType relationType;
    private Double score;
    private Boolean active;
}
