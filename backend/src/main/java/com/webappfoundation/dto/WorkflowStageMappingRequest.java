package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Optional part of a workflow replace (PUT /admin/workflow/{code}) — a Jira-style "publish requires a status
 * mapping". When a stage <b>code</b> that live (non-terminal) orders occupy is being removed, the admin must
 * supply {@code mapping[removedCode] = survivingCode} so those orders can be re-stamped onto a surviving stage;
 * otherwise the save is rejected with 409. Additive changes / label-only renames need no mapping.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStageMappingRequest {
    /** removedStageCode → survivingStageCode (must exist in the new stage set). */
    private Map<String, String> mapping;
}
