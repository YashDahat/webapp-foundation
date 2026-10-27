package com.webappfoundation.dto;

import com.webappfoundation.model.SprintStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/** Read model for a board sprint (header + picker). Closed sprints render the board read-only. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SprintDto {
    private Long id;
    private String workflowCode;
    private String name;
    private LocalDate startsOn;
    private LocalDate endsOn;
    private SprintStatus status;
    private Instant createdAt;
}
