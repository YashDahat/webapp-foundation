package com.webappfoundation.repository;

import com.webappfoundation.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByActiveTrue();

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);

    List<Product> findByActiveTrueAndCategory(String category);

    List<Product> findByActiveTrueAndNameContainingIgnoreCase(String name);

    List<Product> findByIdIn(Collection<Long> ids);

    /** Atomic conditional reserve on the shared product pool — reserves only if enough is available. */
    @Modifying
    @Query("update Product p set p.reserved = p.reserved + :qty " +
            "where p.id = :id and (p.stock - p.reserved) >= :qty")
    int reserveIfAvailable(@Param("id") Long id, @Param("qty") int qty);

    /** Give back held/sold units on the shared product pool (floored at 0 by the guard). */
    @Modifying
    @Query("update Product p set p.reserved = p.reserved - :qty where p.id = :id and p.reserved >= :qty")
    int release(@Param("id") Long id, @Param("qty") int qty);
}
