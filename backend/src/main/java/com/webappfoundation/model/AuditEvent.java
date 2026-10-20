package com.webappfoundation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * An audit record of a user activity — the durable, SQL-queryable source of truth for security/forensics
 * (mirrored to structured logs → Loki for dashboards/alerts). Actor identity is snapshotted
 * ({@code actorEmail}/{@code actorRole}) so the record survives the user being deleted. Never store
 * secrets (passwords, tokens, OTPs) in {@code detail}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_created", columnList = "createdAt"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_user", columnList = "userId")
})
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant createdAt;

    /** Acting user's id (User.id), or null for a guest / system action. */
    private Integer userId;
    /** Snapshot of the actor's email + role at the time (survives user deletion). */
    private String actorEmail;
    private String actorRole;

    /** What happened, e.g. LOGIN_SUCCESS, USER_DELETED (see AuditActions). */
    @Column(nullable = false)
    private String action;
    /** SUCCESS or FAILURE. */
    private String outcome;

    /** What was acted on, e.g. USER / MEDIA / SUPPORT_CASE, and its id. */
    private String targetType;
    private String targetId;

    private String ipAddress;
    private String requestId;

    @Column(columnDefinition = "text")
    private String detail;
}
