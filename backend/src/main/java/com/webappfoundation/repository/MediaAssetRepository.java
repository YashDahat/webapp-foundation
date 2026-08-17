package com.webappfoundation.repository;

import com.webappfoundation.model.MediaAsset;
import com.webappfoundation.model.GallerySection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
    List<MediaAsset> findAllByOrderByUploadedAtDesc();

    // Public gallery = media assets flagged showInGallery, ordered for display.
    List<MediaAsset> findByShowInGalleryTrueOrderBySortOrderAscUploadedAtDesc();
    List<MediaAsset> findByShowInGalleryTrueAndSectionOrderBySortOrderAscUploadedAtDesc(GallerySection section);
}
