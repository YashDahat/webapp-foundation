package com.webappfoundation.repository;

import com.webappfoundation.model.OrderStageEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStageEventRepository extends JpaRepository<OrderStageEvent, Long> {

    /** An order's stage-history timeline, oldest first. */
    List<OrderStageEvent> findByOrderIdOrderByEnteredAtAsc(Long orderId);
}
