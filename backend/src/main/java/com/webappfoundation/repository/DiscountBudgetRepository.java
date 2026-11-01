package com.webappfoundation.repository;

import com.webappfoundation.model.DiscountBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface DiscountBudgetRepository extends JpaRepository<DiscountBudget, Long> {

    Optional<DiscountBudget> findByMonth(String month);

    List<DiscountBudget> findAllByOrderByMonthDesc();

    /** Atomically add to the month's spend-to-date (only when a budget row exists for the month). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DiscountBudget b set b.spentToDate = b.spentToDate + :amount where b.month = :month")
    int addSpend(@Param("month") String month, @Param("amount") BigDecimal amount);
}
