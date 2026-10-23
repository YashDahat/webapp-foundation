package com.webappfoundation.dto;

import com.webappfoundation.model.RecommendationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin view of a {@link com.webappfoundation.model.CatalogRecommendation}. Source/target names are
 * resolved via {@code CatalogService} so the admin grid is readable (null when the item no longer resolves).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationDto {
    private Long id;
    private String sourceItemType;
    private Long sourceItemId;
    private String sourceItemName;
    private String targetItemType;
    private Long targetItemId;
    private String targetItemName;
    private RecommendationType relationType;
    private Double score;
    private boolean active;
}
