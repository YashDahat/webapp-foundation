package com.webappfoundation.dto;

import lombok.Data;

import java.util.List;

/** Customer adds a message to their existing support case (with optional image attachments). */
@Data
public class PostMessageRequest {
    private String body;
    /** Optional image attachment URLs from POST /api/v1/support/attachments. */
    private List<String> attachments;
}
