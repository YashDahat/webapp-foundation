package com.webappfoundation.repository;

import com.webappfoundation.model.Order;
import com.webappfoundation.model.PaymentState;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Read-only aggregation window over the {@link Order} spine for the Analytics screen. This is a
 * <em>separate</em> Spring Data repository on the same entity — it never touches the fenced
 * {@code OrderRepository}/{@code OrderService}, so the order spine stays untouched.
 *
 * <p>"A sale" = an order that reached payment: {@link PaymentState#PAID},
 * {@link PaymentState#REFUND_PENDING} or {@link PaymentState#REFUNDED}. Net sales always subtract any
 * {@code refundAmount}, so refunds correctly reduce revenue. The service loads a bounded window
 * (current financial year + a few weeks of look-back for weekday targets) of rows for per-day/per-user/
 * per-branch breakdowns, and uses the scalar {@link #sumNetSales} for older year-history buckets.
 */
public interface AnalyticsOrderRepository extends Repository<Order, Long> {

    /** Row-level load for a bounded window (half-open is emulated by the service via the upper bound). */
    List<Order> findByPaymentStateInAndCreatedAtBetween(Collection<PaymentState> states, Instant from, Instant to);

    /** Efficient scalar net-sales total for a half-open [from, to) window — used for year-history bars. */
    @Query("select coalesce(sum(o.total - coalesce(o.refundAmount, 0)), 0) from Order o "
            + "where o.paymentState in :states and o.createdAt >= :from and o.createdAt < :to")
    BigDecimal sumNetSales(@Param("states") Collection<PaymentState> states,
                           @Param("from") Instant from, @Param("to") Instant to);

    /** First-ever sale instant per user — drives new-vs-repeat classification without loading all history. */
    @Query("select o.userId as userId, min(o.createdAt) as firstAt from Order o "
            + "where o.paymentState in :states and o.userId is not null group by o.userId")
    List<UserFirstOrder> findFirstOrderInstants(@Param("states") Collection<PaymentState> states);

    /** Earliest sale on record — the left edge of the year-history chart. */
    Optional<Order> findFirstByPaymentStateInOrderByCreatedAtAsc(Collection<PaymentState> states);

    interface UserFirstOrder {
        Integer getUserId();
        Instant getFirstAt();
    }
}
