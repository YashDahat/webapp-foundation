package com.webappfoundation.repository;

import com.webappfoundation.model.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkflowRepository extends JpaRepository<Workflow, Long> {

    Optional<Workflow> findByCode(String code);

    Optional<Workflow> findByIsDefaultTrue();

    boolean existsByCode(String code);
}
