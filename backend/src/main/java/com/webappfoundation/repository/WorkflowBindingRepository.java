package com.webappfoundation.repository;

import com.webappfoundation.model.WorkflowBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowBindingRepository extends JpaRepository<WorkflowBinding, Long> {

    List<WorkflowBinding> findByStageIdAndEnabledTrueOrderByOrdinal(Long stageId);

    List<WorkflowBinding> findByStageIdOrderByOrdinal(Long stageId);

    void deleteByStageId(Long stageId);
}
