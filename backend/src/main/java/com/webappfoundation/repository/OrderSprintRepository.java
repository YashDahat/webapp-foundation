package com.webappfoundation.repository;

import com.webappfoundation.model.OrderSprint;
import com.webappfoundation.model.SprintStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderSprintRepository extends JpaRepository<OrderSprint, Long> {

    /** All sprints for a workflow, newest first — the sprint picker list. */
    List<OrderSprint> findByWorkflowCodeOrderByCreatedAtDesc(String workflowCode);

    /** The workflow's current ACTIVE sprint (invariant: at most one); newest wins if ever duplicated. */
    Optional<OrderSprint> findFirstByWorkflowCodeAndStatusOrderByCreatedAtDesc(String workflowCode, SprintStatus status);

    List<OrderSprint> findByWorkflowCodeAndStatus(String workflowCode, SprintStatus status);
}
