package com.webappfoundation.repository;

import com.webappfoundation.model.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    /** All messages in a case, oldest first (thread order). */
    List<SupportMessage> findByCaseIdOrderByCreatedAtAsc(Long caseId);
}
