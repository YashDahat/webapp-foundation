package com.webappfoundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Admin-facing view of an audit_log row. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventDto {
    private Long id;
    private Instant createdAt;
    private Integer userId;
    private String actorEmail;
    private String actorRole;
    private String action;
    private String outcome;
    private String targetType;
    private String targetId;
    private String ipAddress;
    private String requestId;
    private String detail;
}
