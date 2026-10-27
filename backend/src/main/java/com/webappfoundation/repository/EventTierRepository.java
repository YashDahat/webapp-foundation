package com.webappfoundation.repository;

import com.webappfoundation.model.EventTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventTierRepository extends JpaRepository<EventTier, Long> {

    /** Atomic conditional seat hold on a tier's pool — reserves only if enough is free. */
    @Modifying
    @Query("update EventTier t set t.reserved = t.reserved + :qty " +
            "where t.id = :id and t.stock is not null and (t.stock - t.reserved) >= :qty")
    int reserveIfAvailable(@Param("id") Long id, @Param("qty") int qty);

    /** Give back held/sold seats on a tier's pool (floored at 0 by the guard). */
    @Modifying
    @Query("update EventTier t set t.reserved = t.reserved - :qty where t.id = :id and t.reserved >= :qty")
    int release(@Param("id") Long id, @Param("qty") int qty);
}
