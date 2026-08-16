package com.webappfoundation.repository;

import com.webappfoundation.model.GalleryItem;
import com.webappfoundation.model.GallerySection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GalleryItemRepository extends JpaRepository<GalleryItem, Long> {
    List<GalleryItem> findAllByOrderBySortOrderAscUploadedAtDesc();
    List<GalleryItem> findBySectionOrderBySortOrderAscUploadedAtDesc(GallerySection section);
}
