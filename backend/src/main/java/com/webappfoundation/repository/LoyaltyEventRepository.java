package com.webappfoundation.repository;

import com.webappfoundation.model.LoyaltyEvent;
import com.webappfoundation.model.LoyaltyEventStatus;
import com.webappfoundation.model.LoyaltyEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface LoyaltyEventRepository extends JpaRepository<LoyaltyEvent, Long> {

    List<LoyaltyEvent> findByUserIdAndStatus(Integer userId, LoyaltyEventStatus status);

    boolean existsByUserIdAndType(Integer userId, LoyaltyEventType type);

    long countByUserIdAndTypeAndCreatedAtAfter(Integer userId, LoyaltyEventType type, Instant since);

    long countByUserIdAndTypeNotAndCreatedAtAfter(Integer userId, LoyaltyEventType type, Instant since);

    List<LoyaltyEvent> findByStatusAndConfirmAtBefore(LoyaltyEventStatus status, Instant cutoff);

    List<LoyaltyEvent> findByOrderId(Long orderId);

    /**
     * Sum of points earned since {@code since} across the given (farmable) event types — used to enforce the
     * combined daily/monthly points caps that stop farming of repeatable actions (views/cart/wishlist/…).
     * One-time and high-value events are deliberately excluded by not passing their types.
     */
    @Query("select coalesce(sum(e.points), 0) from LoyaltyEvent e where e.userId = :userId "
            + "and e.type in :types and e.status <> com.webappfoundation.model.LoyaltyEventStatus.REVERSED "
            + "and e.createdAt >= :since")
    BigDecimal sumPointsByTypesSince(@Param("userId") Integer userId,
                                     @Param("types") Collection<LoyaltyEventType> types,
                                     @Param("since") Instant since);
}
