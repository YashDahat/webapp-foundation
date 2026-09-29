package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A customer support ticket. Owned by a user (linked by {@code Integer userId} = User.id — never
 * email/phone). Its conversation lives in {@link SupportMessage} rows (one per message); this row holds
 * the subject, status, and timestamps. {@code updatedAt} bumps on every new message so lists sort by
 * most-recently-active.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "support_case")
public class SupportCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning user's id (User.id). */
    @Column(nullable = false)
    private Integer userId;

    @Column(nullable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
