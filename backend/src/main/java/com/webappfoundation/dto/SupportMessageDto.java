package com.webappfoundation.dto;

import com.webappfoundation.model.SupportAuthor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** One message in a support thread. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportMessageDto {
    private Long id;
    private Long caseId;
    private SupportAuthor author;
    private String body;
    /** Image attachment URLs (media-library /api/v1/media/<key>), in order. */
    private List<String> attachments;
    private Instant createdAt;
}
