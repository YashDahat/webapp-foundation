package com.webappfoundation.config;

import com.webappfoundation.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * One-time, idempotent boot backfill for the order board (plan §9.4). New board columns are added by
 * Hibernate {@code ddl-auto}, but pre-board rows carry nulls — this gives them sane values so the board
 * can place every card. {@code blocked} / {@code carriedForward} default via their column definitions; the
 * only backfill needed here is {@code stageEnteredAt ← updatedAt} so time-in-stage is never null.
 *
 * <p>Sprint bootstrap (≤1 ACTIVE sprint per workflow, orphan adoption) lives in {@code SprintService.bootstrap()},
 * which runs on {@code ApplicationReadyEvent} <em>after</em> workflow seeding (ordered) so a freshly seeded
 * workflow gets its first active sprint.
 */
@Component
@Order(100)
public class OrderBoardBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderBoardBootstrap.class);

    private final OrderRepository orderRepository;

    public OrderBoardBootstrap(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public void run(String... args) {
        int patched = orderRepository.backfillStageEnteredAt();
        if (patched > 0) {
            log.info("Order-board backfill: set stageEnteredAt on {} pre-board order(s)", patched);
        }
    }
}
