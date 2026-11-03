package com.webappfoundation.repository;

import com.webappfoundation.model.Offer;
import com.webappfoundation.model.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    Optional<Offer> findByCode(String code);

    boolean existsByCode(String code);

    List<Offer> findByUserIdAndStatus(Integer userId, OfferStatus status);

    List<Offer> findByUserIdOrderByCreatedAtDesc(Integer userId);

    /**
     * Atomically claim one use of a code — the no-oversell trick (same as inventory). Returns the number
     * of rows updated: 1 = claimed, 0 = already exhausted / concurrent redemption won. Callers must treat
     * 0 as "code unavailable" and reject the redemption.
     */
    @Modifying
    @Query("update Offer o set o.usedCount = o.usedCount + 1 where o.id = :id and o.usedCount < o.maxUses")
    int claimUse(@Param("id") Long id);
}
