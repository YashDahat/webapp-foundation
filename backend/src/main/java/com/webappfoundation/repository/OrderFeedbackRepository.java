package com.webappfoundation.repository;

import com.webappfoundation.model.OrderFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderFeedbackRepository extends JpaRepository<OrderFeedback, Long> {

    Optional<OrderFeedback> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    List<OrderFeedback> findAllByOrderByCreatedAtDesc();
}
