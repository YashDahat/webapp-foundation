package com.webappfoundation.dto;

import lombok.Data;

import java.util.List;

/** Customer opens a new support case: a subject plus the first message (with optional image attachments). */
@Data
public class CreateSupportCaseRequest {
    private String subject;
    private String message;
    /** Optional image attachment URLs from POST /api/v1/support/attachments. */
    private List<String> attachments;
}
