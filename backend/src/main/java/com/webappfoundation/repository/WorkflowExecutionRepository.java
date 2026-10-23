package com.webappfoundation.repository;

import com.webappfoundation.model.WorkflowExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Long> {

    Optional<WorkflowExecution> findByOrderIdAndStageIdAndBindingId(Long orderId, Long stageId, Long bindingId);

    List<WorkflowExecution> findByOrderIdOrderByCreatedAt(Long orderId);
}
