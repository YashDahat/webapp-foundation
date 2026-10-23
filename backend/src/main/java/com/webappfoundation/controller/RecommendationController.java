package com.webappfoundation.controller;

import com.webappfoundation.dto.CatalogItemDto;
import com.webappfoundation.model.RecommendationType;
import com.webappfoundation.service.RecommendationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public recommendation reads. Unauthenticated ({@code anyRequest().permitAll()} in SecurityConfig).
 * Returns resolved {@link CatalogItemDto}s (any sellable type), highest score first. Pass {@code branchId}
 * to drop items not offered at the selected branch.
 */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public List<CatalogItemDto> get(@RequestParam String itemType,
                                    @RequestParam Long itemId,
                                    @RequestParam RecommendationType type,
                                    @RequestParam(required = false) Long branchId) {
        return recommendationService.getRecommendations(itemType, itemId, type, branchId);
    }
}
