package com.webappfoundation.dto;

import com.webappfoundation.model.SupportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Support case summary (list rows). Customer identity is resolved from the owning user into
 * {@code customerName}/{@code customerEmail} for the admin helpdesk — the stored link is {@code userId}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportCaseDto {
    private Long id;
    private Integer userId;
    private String customerName;
    private String customerEmail;
    private String subject;
    private SupportStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
