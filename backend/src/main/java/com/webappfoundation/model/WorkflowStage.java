package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One ordered stage of a {@link Workflow}. An order enters the {@code initial} stage at placeOrder and the
 * {@code onPaidEntry} stage on payment capture (or immediately for pickup pay-at-counter / ₹0). Each stage
 * carries its own cancellation policy ({@code cancellable} + {@code refundPolicy} + {@code refundPercent}),
 * so cancellation rules differ per business automatically. {@code (workflowCode, code)} is unique; a bare
 * {@code code} like {@code OUT_FOR_DELIVERY} MAY repeat across workflows — always look up scoped by workflow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "workflow_stage",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workflowCode", "code"}))
public class WorkflowStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String workflowCode;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private int ordinal;

    /** Order enters here on placeOrder. */
    @Column(nullable = false)
    @Builder.Default
    private boolean initial = false;

    /** Entered on payment capture (or immediately for pickup pay-at-counter / ₹0). */
    @Column(nullable = false)
    @Builder.Default
    private boolean onPaidEntry = false;

    /** Completed/cancelled. */
    @Column(nullable = false)
    @Builder.Default
    private boolean terminal = false;

    // ── per-stage cancellation policy ──
    @Column(nullable = false)
    @Builder.Default
    private boolean cancellable = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RefundPolicy refundPolicy = RefundPolicy.FULL;

    /** For PARTIAL (0-100); ignored for FULL/NONE. */
    private Integer refundPercent;

    /** Order-board SLA target in minutes for time-in-stage; "stuck" = time-in-stage &gt; target. Nullable = no target. */
    private Integer targetMinutes;
}
