package com.webappfoundation.repository;

import com.webappfoundation.model.Order;
import com.webappfoundation.model.PaymentState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDesc(Integer userId);

    Optional<Order> findByReferenceId(String referenceId);

    List<Order> findAllByOrderByCreatedAtDesc();

    /** Abandoned-online-payment sweep: orders stuck awaiting payment past the TTL cutoff. */
    List<Order> findByPaymentStateAndCreatedAtBefore(PaymentState paymentState, Instant cutoff);

    // ── Order-board scoped reads ──
    List<Order> findByWorkflowCodeAndSprintId(String workflowCode, Long sprintId);
    List<Order> findByWorkflowCodeAndSprintIdAndBranchId(String workflowCode, Long sprintId, Long branchId);
    List<Order> findByWorkflowCodeAndCurrentStageCodeIn(String workflowCode, List<String> stageCodes);
    List<Order> findBySprintIdIsNullAndWorkflowCode(String workflowCode);

    /** One-time boot backfill (§9.4): give pre-board rows a sane time-in-stage source. */
    @Modifying
    @Transactional
    @Query("update Order o set o.stageEnteredAt = o.updatedAt where o.stageEnteredAt is null")
    int backfillStageEnteredAt();
}
