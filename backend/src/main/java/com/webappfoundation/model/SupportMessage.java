package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One message in a {@link SupportCase} thread, written by the CUSTOMER or by STAFF. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "support_message")
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The SupportCase this message belongs to (SupportCase.id). */
    @Column(nullable = false)
    private Long caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportAuthor author;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    // Optional ordered image attachments — media-library URLs (/api/v1/media/<key>). EAGER so the
    // thread can be mapped to a DTO outside an open session.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "support_message_attachments", joinColumns = @JoinColumn(name = "message_id"))
    @OrderColumn(name = "attachment_order")
    @Column(name = "image_url")
    @Builder.Default
    private List<String> attachments = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;
}
