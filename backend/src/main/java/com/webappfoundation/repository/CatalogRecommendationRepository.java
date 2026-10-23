package com.webappfoundation.repository;

import com.webappfoundation.model.CatalogRecommendation;
import com.webappfoundation.model.RecommendationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CatalogRecommendationRepository extends JpaRepository<CatalogRecommendation, Long> {

    /** Active recommendations for a source item + relation, highest score first. */
    List<CatalogRecommendation> findBySourceItemTypeAndSourceItemIdAndRelationTypeAndActiveTrueOrderByScoreDesc(
            String sourceItemType, Long sourceItemId, RecommendationType relationType);

    /** For dedupe on create/import. */
    Optional<CatalogRecommendation> findBySourceItemTypeAndSourceItemIdAndTargetItemTypeAndTargetItemIdAndRelationType(
            String sourceItemType, Long sourceItemId, String targetItemType, Long targetItemId,
            RecommendationType relationType);
}
