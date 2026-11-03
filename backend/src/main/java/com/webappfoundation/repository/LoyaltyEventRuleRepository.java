package com.webappfoundation.repository;

import com.webappfoundation.model.LoyaltyEventRule;
import com.webappfoundation.model.LoyaltyEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoyaltyEventRuleRepository extends JpaRepository<LoyaltyEventRule, Long> {

    Optional<LoyaltyEventRule> findByEventType(LoyaltyEventType eventType);

    boolean existsByEventType(LoyaltyEventType eventType);
}
