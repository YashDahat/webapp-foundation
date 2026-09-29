package com.webappfoundation.repository;

import com.webappfoundation.model.SupportCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportCaseRepository extends JpaRepository<SupportCase, Long> {
    /** The current customer's cases, most-recently-active first. */
    List<SupportCase> findByUserIdOrderByUpdatedAtDesc(Integer userId);

    /** All cases (admin helpdesk), most-recently-active first. */
    List<SupportCase> findAllByOrderByUpdatedAtDesc();
}
