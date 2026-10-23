package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Records one connector-action invocation for an {@code (orderId, stageId, bindingId)}. Drives the
 * dispatcher's dedupe: a binding already recorded {@code SUCCESS} is skipped on a duplicate event (unless an
 * explicit admin {@code RETRY}), so a redelivered webhook never re-emails/-ships/-refunds. Surfaced in the
 * admin order timeline with a Retry action for failures.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "workflow_execution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"orderId", "stageId", "bindingId"}))
public class WorkflowExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long stageId;

    @Column(nullable = false)
    private Long bindingId;

    private String connectorId;
    private String actionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
