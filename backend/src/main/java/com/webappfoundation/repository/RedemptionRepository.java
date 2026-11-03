package com.webappfoundation.repository;

import com.webappfoundation.model.Redemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RedemptionRepository extends JpaRepository<Redemption, Long> {

    List<Redemption> findByOrderId(Long orderId);

    List<Redemption> findByUserId(Integer userId);

    boolean existsByOrderId(Long orderId);
}
