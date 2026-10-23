package com.webappfoundation.event;

import com.webappfoundation.model.TriggerType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published by {@code WorkflowEngine} after an order enters a stage (after the transition commits). The
 * {@code StageActionDispatcher} listens async and fans the stage's enabled bindings out to their connectors.
 * Carries only ids/codes (no entity) to keep the domain → connector direction and avoid a bean cycle.
 */
@Getter
public class OrderStageEnteredEvent extends ApplicationEvent {

    private final Long orderId;
    private final String workflowCode;
    private final String stageCode;
    private final TriggerType trigger;

    public OrderStageEnteredEvent(Object source, Long orderId, String workflowCode, String stageCode, TriggerType trigger) {
        super(source);
        this.orderId = orderId;
        this.workflowCode = workflowCode;
        this.stageCode = stageCode;
        this.trigger = trigger;
    }
}
