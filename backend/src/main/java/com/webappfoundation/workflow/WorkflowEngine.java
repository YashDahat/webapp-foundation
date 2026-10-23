package com.webappfoundation.workflow;

import com.webappfoundation.event.OrderStageEnteredEvent;
import com.webappfoundation.model.Order;
import com.webappfoundation.model.TriggerType;
import com.webappfoundation.model.WorkflowStage;
import com.webappfoundation.repository.OrderRepository;
import com.webappfoundation.repository.WorkflowStageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * Drives order stage transitions. {@code advance} validates the target stage (scoped by the order's
 * {@code workflowCode}), sets {@code currentStageCode}, persists, and publishes {@link OrderStageEnteredEvent}
 * — the dispatcher then fans out the stage's bound actions after commit.
 *
 * <p><b>Idempotent for automatic triggers</b> ({@code SYSTEM}/{@code PAYMENT}/{@code WEBHOOK}): if the order
 * is already at or past the target stage it is a no-op, so a redelivered payment/connector webhook can't
 * double-advance. Only {@code ADMIN} may deliberately re-enter a stage.
 */
@Service
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final WorkflowStageRepository stageRepository;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher events;

    public WorkflowEngine(WorkflowStageRepository stageRepository, OrderRepository orderRepository,
                          ApplicationEventPublisher events) {
        this.stageRepository = stageRepository;
        this.orderRepository = orderRepository;
        this.events = events;
    }

    /** Advance (or re-enter, for ADMIN) {@code order} to {@code toStageCode}. Returns true if it moved. */
    @Transactional
    public boolean advance(Order order, String toStageCode, TriggerType trigger) {
        List<WorkflowStage> stages = stageRepository.findByWorkflowCodeOrderByOrdinal(order.getWorkflowCode());
        WorkflowStage target = stages.stream()
                .filter(s -> s.getCode().equals(toStageCode))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown stage '" + toStageCode + "' for workflow '" + order.getWorkflowCode() + "'"));

        boolean automatic = trigger == TriggerType.SYSTEM || trigger == TriggerType.PAYMENT
                || trigger == TriggerType.WEBHOOK;
        if (automatic) {
            int currentOrdinal = currentOrdinal(order, stages);
            if (currentOrdinal >= target.getOrdinal()) {
                log.debug("Order {} already at/past stage {} (trigger {}) — no-op", order.getId(), toStageCode, trigger);
                return false; // idempotent: never double-advance on a redelivered automatic trigger
            }
        }

        order.setCurrentStageCode(toStageCode);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        events.publishEvent(new OrderStageEnteredEvent(this, order.getId(), order.getWorkflowCode(), toStageCode, trigger));
        return true;
    }

    /** Ordinal of the order's current stage, or -1 when it has none yet (just placed). */
    private int currentOrdinal(Order order, List<WorkflowStage> stages) {
        if (order.getCurrentStageCode() == null) {
            return -1;
        }
        return stages.stream()
                .filter(s -> s.getCode().equals(order.getCurrentStageCode()))
                .map(WorkflowStage::getOrdinal)
                .findFirst()
                .orElse(-1);
    }
}
