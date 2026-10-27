package com.webappfoundation.dto;

import com.webappfoundation.model.BlockReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * The board detail-sheet read model: the full (customer-shaped) {@code OrderDto} plus the board-only overlay
 * (assignee, blocked state, time-in-stage vs target, channel, sprint) and the stage-history {@code timeline}.
 * Returned by the admin {@code GET /orders/{id}} so one fetch backs the detail sheet.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBoardDetailDto {
    private OrderDto order;
    private String customerName;
    private String stageLabel;
    private Integer assignedUserId;
    private String assignedStaffName;
    private boolean blocked;
    private BlockReason blockReason;
    private String blockNote;
    private Instant blockedAt;
    private Instant stageEnteredAt;
    private Integer targetMinutes;
    private long minutesInStage;
    private boolean stuck;
    private boolean carriedForward;
    private Long sprintId;
    private String channel;
    private List<OrderStageEventDto> timeline;
}
