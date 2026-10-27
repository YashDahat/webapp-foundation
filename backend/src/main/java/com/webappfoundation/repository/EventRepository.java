package com.webappfoundation.repository;

import com.webappfoundation.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByActiveTrue();

    Optional<Event> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);

    List<Event> findByActiveTrueAndCategory(String category);

    List<Event> findByActiveTrueAndNameContainingIgnoreCase(String name);

    List<Event> findByIdIn(Collection<Long> ids);

    /** Atomic conditional seat hold on the event's shared capacity pool — reserves only if enough is free. */
    @Modifying
    @Query("update Event e set e.reserved = e.reserved + :qty " +
            "where e.id = :id and e.capacity is not null and (e.capacity - e.reserved) >= :qty")
    int reserveIfAvailable(@Param("id") Long id, @Param("qty") int qty);

    /** Give back held/sold seats on the shared capacity pool (floored at 0 by the guard). */
    @Modifying
    @Query("update Event e set e.reserved = e.reserved - :qty where e.id = :id and e.reserved >= :qty")
    int release(@Param("id") Long id, @Param("qty") int qty);
}
