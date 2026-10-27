package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Create or edit a sprint. {@code id == null} creates a new ACTIVE sprint (rejected with 409 if the workflow
 * already has an active one — complete it first); a present {@code id} edits that sprint's name/dates only
 * (status transitions happen via {@code POST /sprints/{id}/complete}, never here).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertSprintRequest {
    private Long id;
    private String workflowCode;
    private String name;
    private LocalDate startsOn;
    private LocalDate endsOn;
}
