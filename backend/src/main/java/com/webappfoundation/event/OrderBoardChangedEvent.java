package com.webappfoundation.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published after a board-affecting order mutation that is NOT a stage transition (block/resume/assign/refund/
 * mark-paid/placement) so the live board (SSE) can push a refresh. Stage moves already publish
 * {@link OrderStageEnteredEvent}; {@code OrderBoardStream} listens to both. Carries only the {@code workflowCode}
 * (the board scope key) — viewers refetch the board on receipt.
 */
@Getter
public class OrderBoardChangedEvent extends ApplicationEvent {

    private final String workflowCode;

    public OrderBoardChangedEvent(Object source, String workflowCode) {
        super(source);
        this.workflowCode = workflowCode;
    }
}
