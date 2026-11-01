package com.webappfoundation.repository;

import com.webappfoundation.model.LoyaltyTier;
import com.webappfoundation.model.Tier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoyaltyTierRepository extends JpaRepository<LoyaltyTier, Long> {

    Optional<LoyaltyTier> findByTier(Tier tier);

    boolean existsByTier(Tier tier);

    List<LoyaltyTier> findAllByOrderByMinScoreAsc();
}
