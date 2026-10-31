package com.webappfoundation.repository;

import com.webappfoundation.model.DailyBusinessLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyBusinessLogRepository extends JpaRepository<DailyBusinessLog, Long> {

    Optional<DailyBusinessLog> findByLogDate(LocalDate logDate);

    /** Inclusive on both ends — used to pull a period's worth of cost/complaint entries in one shot. */
    List<DailyBusinessLog> findByLogDateBetween(LocalDate from, LocalDate to);

    boolean existsByLogDate(LocalDate logDate);

    List<DailyBusinessLog> findAllByOrderByLogDateDesc();
}
