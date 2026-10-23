package com.webappfoundation.repository;

import com.webappfoundation.model.Order;
import com.webappfoundation.model.PaymentState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDesc(Integer userId);

    Optional<Order> findByReferenceId(String referenceId);

    List<Order> findAllByOrderByCreatedAtDesc();

    /** Abandoned-online-payment sweep: orders stuck awaiting payment past the TTL cutoff. */
    List<Order> findByPaymentStateAndCreatedAtBefore(PaymentState paymentState, Instant cutoff);
}
