package com.webappfoundation.dto;

import com.webappfoundation.model.TriggerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One entry in an order's stage-history timeline (board detail panel), projected from {@code OrderStageEvent}
 * with the stage's human label resolved. {@code migration=true} marks a workflow-edit remap (not a real move).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStageEventDto {
    private Long id;
    private String stageCode;
    private String stageLabel;
    private Instant enteredAt;
    private TriggerType trigger;
    private Integer byUserId;
    private boolean migration;
}
