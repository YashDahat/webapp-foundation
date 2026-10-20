package com.webappfoundation.service;

import com.webappfoundation.model.AuditEvent;
import com.webappfoundation.model.User;
import com.webappfoundation.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Records user-activity audit events to BOTH the durable {@code audit_log} table (SQL source of truth)
 * and the structured {@code AUDIT} logger (JSON → Loki for dashboards/alerts). NEVER throws — an audit
 * failure is logged, not propagated, so it can never break the business flow that triggered it. The
 * actor is resolved from the security context unless supplied explicitly (e.g. a failed login).
 *
 * Do NOT put secrets (passwords, tokens, OTP codes, full PII) in {@code detail}.
 */
@Service
public class AuditService {

    private static final Logger audit = LoggerFactory.getLogger("AUDIT");

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    /** Record an event attributed to the current authenticated user (or guest if none). */
    public void record(String action, String outcome, String targetType, String targetId, String detail) {
        User actor = currentUser();
        Integer userId = actor == null ? null : actor.getId();
        String email = actor == null ? null : actor.getEmail();
        String role = actor == null || actor.getRole() == null ? null : actor.getRole().name();
        save(userId, email, role, action, outcome, targetType, targetId, detail);
    }

    /** Record an event with an explicit actor — for flows where the principal isn't set (e.g. login). */
    public void recordActor(Integer userId, String actorEmail, String actorRole, String action,
                            String outcome, String targetType, String targetId, String detail) {
        save(userId, actorEmail, actorRole, action, outcome, targetType, targetId, detail);
    }

    private void save(Integer userId, String actorEmail, String actorRole, String action, String outcome,
                      String targetType, String targetId, String detail) {
        String requestId = MDC.get("requestId");
        String ip = MDC.get("ip");
        try {
            repository.save(AuditEvent.builder()
                    .createdAt(Instant.now())
                    .userId(userId)
                    .actorEmail(actorEmail)
                    .actorRole(actorRole)
                    .action(action)
                    .outcome(outcome)
                    .targetType(targetType)
                    .targetId(targetId)
                    .ipAddress(ip)
                    .requestId(requestId)
                    .detail(detail)
                    .build());
        } catch (Exception e) {
            audit.error("Failed to persist audit event {}: {}", action, e.getMessage());
        }
        // Structured line for Loki (kv args become JSON fields via the logstash encoder).
        audit.info("audit {} {}", action, outcome,
                kv("action", action), kv("outcome", outcome), kv("userId", userId),
                kv("actorEmail", actorEmail), kv("actorRole", actorRole),
                kv("targetType", targetType), kv("targetId", targetId),
                kv("ip", ip), kv("requestId", requestId), kv("detail", detail));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth.getPrincipal() instanceof User user ? user : null;
    }
}
