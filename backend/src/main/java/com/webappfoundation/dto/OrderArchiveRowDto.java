package com.webappfoundation.dto;

import com.webappfoundation.model.FulfillmentType;
import com.webappfoundation.model.PaymentState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of the admin <b>All orders</b> archive — a flat, cross-sprint / cross-workflow record view that
 * complements the sprint-scoped board. Carries the workflow + sprint + stage context (which the board shows
 * only one slice of at a time) so an admin can review every order ever placed, filterable and sortable.
 * ADMIN-only (never in the STAFF allowlist). Board-internal fields stay off the customer {@code OrderDto}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderArchiveRowDto {
    private Long id;
    private String orderNumber;
    private String customerName;
    private String workflowCode;
    private String workflowLabel;
    private String stageCode;
    private String stageLabel;
    private boolean blocked;
    private Long sprintId;
    private String sprintName;
    private PaymentState paymentState;
    private FulfillmentType fulfillmentType;
    private Long branchId;
    private String branchName;
    private String assignedStaffName;
    private BigDecimal total;
    private String currency;
    private boolean carriedForward;
    private Instant createdAt;
    private Instant updatedAt;
}
