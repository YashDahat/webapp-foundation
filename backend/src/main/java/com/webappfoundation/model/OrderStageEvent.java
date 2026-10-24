package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One entry in an order's stage-history timeline (shown in the board detail panel). Appended by
 * {@link com.webappfoundation.workflow.WorkflowEngine} on every transition and at placement. A
 * {@code trigger} of {@link TriggerType#ADMIN} with {@code migration=true} marks a bulk re-stamp from a
 * workflow edit (stage remap) rather than a real lifecycle transition. Block/resume/assign actions are
 * NOT recorded here — they live in {@code audit_log}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_stage_event")
public class OrderStageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private String stageCode;

    @Column(nullable = false)
    private Instant enteredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriggerType trigger;

    /** Acting user (User.id) when known (e.g. admin stage change); null for automatic transitions. */
    private Integer byUserId;

    /** True when this entry is a workflow-edit stage remap (migration, not a real transition). */
    @Column(nullable = false)
    @Builder.Default
    private boolean migration = false;
}
