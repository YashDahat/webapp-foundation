package com.webappfoundation.dto;

import com.webappfoundation.model.SupportStatus;
import lombok.Data;

import java.util.List;

/** Admin replies to a support case: a STAFF message and, optionally, a new status. */
@Data
public class AdminReplyRequest {
    private String body;
    /** Optional — change the case status alongside the reply (null leaves it unchanged). */
    private SupportStatus status;
    /** Optional image attachment URLs from POST /api/v1/support/attachments. */
    private List<String> attachments;
}
