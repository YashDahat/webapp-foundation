package com.webappfoundation.repository;

import com.webappfoundation.model.WorkflowStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowStageRepository extends JpaRepository<WorkflowStage, Long> {

    List<WorkflowStage> findByWorkflowCodeOrderByOrdinal(String workflowCode);

    Optional<WorkflowStage> findByWorkflowCodeAndCode(String workflowCode, String code);

    Optional<WorkflowStage> findByWorkflowCodeAndInitialTrue(String workflowCode);

    Optional<WorkflowStage> findByWorkflowCodeAndOnPaidEntryTrue(String workflowCode);

    long countByWorkflowCodeAndInitialTrue(String workflowCode);

    long countByWorkflowCodeAndOnPaidEntryTrue(String workflowCode);
}
