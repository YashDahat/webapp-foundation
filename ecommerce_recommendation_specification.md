# Cross-Sell, Upsell, and Post-Purchase Technical Specification Document
**Version:** 1.0.0  
**Stack:** React (Frontend) | Spring Boot (Backend) | Relational Database (SQL)

---

## 1. UI/UX User Flows & Wireframe Specifications

### 1.1 Product Detail Page (Upsell Engine)
*   **Placement:** Placed directly beneath the main product image and description layout.
*   **Visual Design:** A subtle grid titled *"Upgrade Options"* or *"Alternative Choices"*.
*   **Behavior:** Displays highly relevant, higher-value variations. Clicking a card redirects the user smoothly to that premium product page.

### 1.2 Cart & Checkout Page (Cross-Sell Engine)
*   **Placement:** Injected into the slide-out cart drawer or directly below the line items on the main Checkout page.
*   **Visual Design:** An explicit carousel or row titled *"Frequently Bought Together"*.
*   **Behavior:** Features small product thumbnails, price points, and a prominent one-click `Add` button that immediately triggers a state update appending the item into the current cart payload without forcing a page reload.

### 1.3 Post-Purchase Interception (After-Sales Engine)
*   **Placement:** A full-screen dedicated transitional page intercepting the pipeline *after* checkout submission but *before* loading the native confirmation receipt.
*   **Visual Design:** High-contrast countdown timer (e.g., "Offer expires in 05:00 minutes") emphasizing a single, high-discount accessory or bundle extension.
*   **Behavior:** Contains two prominent buttons: 
    1.  `[Yes, Add to My Order!]` — One-click authorization charging the tokenized card.
    2.  `[No thanks, show my receipt]` — Gracefully routes to standard thank-you screen.

---

## 2. Relational Database Schema (DDL)

```sql
-- Core recommendations table defining source-target relationships
CREATE TABLE product_recommendations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_product_id BIGINT NOT NULL,
    recommended_product_id BIGINT NOT NULL,
    relation_type VARCHAR(30) NOT NULL, -- 'UPSELL', 'CROSS_SELL', 'POST_PURCHASE'
    score DOUBLE DEFAULT 0.0,            -- Recommender confidence score or weight
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_source_product FOREIGN KEY (source_product_id) REFERENCES products(id),
    CONSTRAINT fk_rec_product FOREIGN KEY (recommended_product_id) REFERENCES products(id),
    CONSTRAINT uq_source_rec_type UNIQUE (source_product_id, recommended_product_id, relation_type)
);

-- Indexing for fast runtime evaluation
CREATE INDEX idx_recommendations_lookup 
ON product_recommendations(source_product_id, relation_type, is_active);
```

---

## 3. Spring Boot Backend Code Architecture

### 3.1 Domain Entities & Repositories
```java
package com.ecommerce.recommendation.domain;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "product_recommendations")
@Data
public class ProductRecommendation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_product_id", nullable = false)
    private Long sourceProductId;

    @Column(name = "recommended_product_id", nullable = false)
    private Long recommendedProductId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false)
    private RecommendationType relationType;

    private Double score;
    private Boolean isActive;
}

public enum RecommendationType {
    UPSELL, CROSS_SELL, POST_PURCHASE;
}
```

```java
package com.ecommerce.recommendation.repository;

import com.ecommerce.recommendation.domain.ProductRecommendation;
import com.ecommerce.recommendation.domain.RecommendationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ProductRecommendationRepository extends JpaRepository<ProductRecommendation, Long> {
    
    @Query("SELECT r.recommendedProductId FROM ProductRecommendation r " +
           "WHERE r.sourceProductId = :sourceProductId " +
           "AND r.relationType = :type " +
           "AND r.isActive = true " +
           "ORDER BY r.score DESC")
    List<Long> findRecommendedProductIds(Long sourceProductId, RecommendationType type);
}
```

### 3.2 Service Tier
```java
package com.ecommerce.recommendation.service;

import com.ecommerce.recommendation.repository.ProductRecommendationRepository;
import com.ecommerce.recommendation.domain.RecommendationType;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RecommendationService {

    private final ProductRecommendationRepository recommendationRepository;
    private final ProductClient productClient; // Feigns an internal client/repository fetching fully built product data

    public RecommendationService(ProductRecommendationRepository recommendationRepository, ProductClient productClient) {
        this.recommendationRepository = recommendationRepository;
        this.productClient = productClient;
    }

    public List<ProductDto> getRecommendationsForProduct(Long productId, RecommendationType type) {
        List<Long> targetIds = recommendationRepository.findRecommendedProductIds(productId, type);
        return productClient.fetchProductsByIds(targetIds);
    }
}
```

### 3.3 Controller Rest Endpoints
```java
package com.ecommerce.recommendation.controller;

import com.ecommerce.recommendation.service.RecommendationService;
import com.ecommerce.recommendation.domain.RecommendationType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/recommendations")
@CrossOrigin(origins = "*")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/target")
    public ResponseEntity<List<ProductDto>> fetchRecommendations(
            @RequestParam Long productId,
            @RequestParam RecommendationType type) {
        return ResponseEntity.ok(recommendationService.getRecommendationsForProduct(productId, type));
    }
}
```

---

## 4. React Frontend Component Implementation

### 4.1 Custom Integration Hook (`useRecommendationEngine.js`)
```javascript
import { useState, useEffect } from 'react';

export const useRecommendationEngine = (productId, type) => {
  const [data, setData] = useState([]);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!productId) return;
    setIsLoading(true);
    
    const apiEndpoint = `http://localhost:8080/api/v1/recommendations/target?productId=${productId}&type=${type}`;
    
    fetch(apiEndpoint)
      .then((res) => (res.status === 204 ? [] : res.json()))
      .then((results) => {
        setData(results);
        setIsLoading(false);
      })
      .catch((err) => {
        console.error(`Error loading recommendation type ${type}:`, err);
        setIsLoading(false);
      });
  }, [productId, type]);

  return { data, isLoading };
};
```

### 4.2 Reusable Unified Engine Layout Component (`RecommendationWidget.jsx`)
```jsx
import React from 'react';
import { useRecommendationEngine } from './useRecommendationEngine';

export const RecommendationWidget = ({ productId, type, title, onAction }) => {
  const { data, isLoading } = useRecommendationEngine(productId, type);

  if (isLoading) return <div className="widget-loader">Fetching target recommendations...</div>;
  if (!data || data.length === 0) return null;

  return (
    <section className={`rec-section-${type.toLowerCase()}`} style={{ padding: '20px 0' }}>
      <h3 style={{ fontSize: '1.4rem', marginBottom: '12px' }}>{title}</h3>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: '16px' }}>
        {data.map((product) => (
          <div key={product.id} className="rec-card" style={{ border: '1px solid #eaeaea', padding: '12px', borderRadius: '8px' }}>
            <img src={product.imageUrl} alt={product.name} style={{ width: '100%', borderRadius: '4px' }} />
            <h4 style={{ margin: '8px 0 4px 0', fontSize: '1rem' }}>{product.name}</h4>
            <p style={{ color: '#0070f3', fontWeight: 'bold' }}>${product.price.toFixed(2)}</p>
            <button 
              onClick={() => onAction(product)}
              style={{
                width: '100%',
                padding: '8px',
                background: '#0070f3',
                color: '#fff',
                border: 'none',
                borderRadius: '4px',
                cursor: 'pointer'
              }}
            >
              {type === 'UPSELL' ? 'View Details' : 'Add to Cart'}
            </button>
          </div>
        ))}
      </div>
    </section>
  );
};
```
