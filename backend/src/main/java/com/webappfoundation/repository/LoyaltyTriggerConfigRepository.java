package com.webappfoundation.repository;

import com.webappfoundation.model.LoyaltyTriggerConfig;
import com.webappfoundation.model.OfferTrigger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoyaltyTriggerConfigRepository extends JpaRepository<LoyaltyTriggerConfig, Long> {

    Optional<LoyaltyTriggerConfig> findByTrigger(OfferTrigger trigger);

    boolean existsByTrigger(OfferTrigger trigger);
}
