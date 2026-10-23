package com.webappfoundation.repository;

import com.webappfoundation.model.BranchStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BranchStockRepository extends JpaRepository<BranchStock, Long> {

    List<BranchStock> findByProductId(Long productId);

    Optional<BranchStock> findByProductIdAndVariantIdAndBranchId(Long productId, Long variantId, Long branchId);

    Optional<BranchStock> findByProductIdAndVariantIdIsNullAndBranchId(Long productId, Long branchId);

    void deleteByProductId(Long productId);

    /** Atomic conditional reserve on a per-branch row — reserves only if enough is available. */
    @Modifying
    @Query("update BranchStock b set b.reserved = b.reserved + :qty " +
            "where b.id = :id and (b.stock - b.reserved) >= :qty")
    int reserveIfAvailable(@Param("id") Long id, @Param("qty") int qty);

    /** Give back held/sold units on a per-branch row (floored at 0 by the guard). */
    @Modifying
    @Query("update BranchStock b set b.reserved = b.reserved - :qty where b.id = :id and b.reserved >= :qty")
    int release(@Param("id") Long id, @Param("qty") int qty);
}
