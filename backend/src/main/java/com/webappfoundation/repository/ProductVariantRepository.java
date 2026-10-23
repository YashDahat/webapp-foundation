package com.webappfoundation.repository;

import com.webappfoundation.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    /** Atomic conditional reserve on a variant's shared pool — reserves only if enough is available. */
    @Modifying
    @Query("update ProductVariant v set v.reserved = v.reserved + :qty " +
            "where v.id = :id and (v.stock - v.reserved) >= :qty")
    int reserveIfAvailable(@Param("id") Long id, @Param("qty") int qty);

    /** Give back held/sold units on a variant's shared pool (floored at 0 by the guard). */
    @Modifying
    @Query("update ProductVariant v set v.reserved = v.reserved - :qty where v.id = :id and v.reserved >= :qty")
    int release(@Param("id") Long id, @Param("qty") int qty);
}
