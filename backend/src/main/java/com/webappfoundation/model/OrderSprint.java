package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A time-boxed batch of orders on the board, scoped to one {@code workflowCode} (sprints are per workflow,
 * not per branch — branch is a filter within a sprint). Orders join the workflow's {@code ACTIVE} sprint at
 * placement ({@code Order.sprintId}); completing a sprint closes it, opens the next, and carries forward the
 * ticked open orders. Invariant: at most one {@code ACTIVE} sprint per workflow (see {@code SprintService}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_sprint")
public class OrderSprint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String workflowCode;

    @Column(nullable = false)
    private String name;

    /** Inclusive planning window shown in the board header; both nullable (open-ended). */
    private LocalDate startsOn;
    private LocalDate endsOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SprintStatus status;

    @Column(nullable = false)
    private Instant createdAt;
}
