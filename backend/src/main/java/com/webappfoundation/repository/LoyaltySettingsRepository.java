package com.webappfoundation.repository;

import com.webappfoundation.model.LoyaltySettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltySettingsRepository extends JpaRepository<LoyaltySettings, Long> {
}
