package com.webappfoundation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.webappfoundation.model.RefundPolicy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** Full admin view/edit shape of a workflow: its ordered stages, each with its cancellation policy + bindings. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDto {
    private String code;
    private String label;
    /** Serialized/deserialized as "default" (the field is `isDefault`), bound on both sides via @JsonProperty. */
    @JsonProperty("default")
    private boolean isDefault;
    private List<StageDto> stages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageDto {
        private Long id;
        private String code;
        private String label;
        private int ordinal;
        private boolean initial;
        private boolean onPaidEntry;
        private boolean terminal;
        private boolean cancellable;
        private RefundPolicy refundPolicy;
        private Integer refundPercent;
        private List<BindingDto> bindings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BindingDto {
        private Long id;
        private String connectorId;
        private String actionCode;
        private int ordinal;
        private boolean enabled;
        private Map<String, String> params;
    }
}
