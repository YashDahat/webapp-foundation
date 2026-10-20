package com.webappfoundation.repository;

import com.webappfoundation.model.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    Page<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<AuditEvent> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);
    Page<AuditEvent> findByActorEmailContainingIgnoreCaseOrderByCreatedAtDesc(String actorEmail, Pageable pageable);
}
