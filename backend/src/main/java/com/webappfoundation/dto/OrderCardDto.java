package com.webappfoundation.dto;

import com.webappfoundation.model.BlockReason;
import com.webappfoundation.model.FulfillmentType;
import com.webappfoundation.model.PaymentMode;
import com.webappfoundation.model.PaymentState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single order rendered as a board card. Carries the board-only fields (blocked/assignee/stuck/channel) that
 * are deliberately kept off the customer {@code OrderDto}. {@code minutesInStage} freezes while blocked;
 * {@code stuck} = time-in-stage past the stage's {@code targetMinutes} (never true while blocked or terminal).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCardDto {
    private Long id;
    private String orderNumber;
    private String customerName;
    private BigDecimal total;
    private String currency;
    private int itemCount;
    private FulfillmentType fulfillmentType;
    private PaymentMode paymentMode;
    private PaymentState paymentState;
    private Long branchId;
    private String branchName;
    private Integer assignedUserId;
    private String assignedStaffName;
    private boolean blocked;
    private BlockReason blockReason;
    private String channel;
    private String stageCode;
    private Instant stageEnteredAt;
    private Instant blockedAt;
    private Integer targetMinutes;
    private long minutesInStage;
    private boolean stuck;
    private boolean carriedForward;
    private Instant createdAt;
}
