package com.webappfoundation.repository;

import com.webappfoundation.model.CoinLedgerEntry;
import com.webappfoundation.model.CoinStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CoinLedgerEntryRepository extends JpaRepository<CoinLedgerEntry, Long> {

    List<CoinLedgerEntry> findByUserIdOrderByEarnedAtDesc(Integer userId);

    List<CoinLedgerEntry> findByUserIdAndStatus(Integer userId, CoinStatus status);

    List<CoinLedgerEntry> findByOrderId(Long orderId);

    List<CoinLedgerEntry> findByStatusAndExpiresAtBefore(CoinStatus status, Instant cutoff);

    List<CoinLedgerEntry> findByStatusAndEarnedAtBefore(CoinStatus status, Instant cutoff);
}
