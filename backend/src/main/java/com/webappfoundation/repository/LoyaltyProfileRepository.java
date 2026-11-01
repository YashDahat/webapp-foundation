package com.webappfoundation.repository;

import com.webappfoundation.model.LoyaltyProfile;
import com.webappfoundation.model.Tier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LoyaltyProfileRepository extends JpaRepository<LoyaltyProfile, Integer> {

    List<LoyaltyProfile> findByTier(Tier tier);

    long countByTier(Tier tier);

    long countByInHoldoutTrue();

    /**
     * Atomically adjust the spendable coin balance, refusing to go negative — the same no-oversell guard used
     * by inventory/offers. Returns rows updated: 1 = applied, 0 = would overdraw (reject the redemption).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LoyaltyProfile p set p.coinBalance = p.coinBalance + :delta "
            + "where p.userId = :userId and p.coinBalance + :delta >= 0")
    int adjustCoinBalance(@Param("userId") Integer userId, @Param("delta") BigDecimal delta);
}
