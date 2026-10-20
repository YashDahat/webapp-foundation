package com.webappfoundation.controller.admin;

import com.webappfoundation.dto.AuditEventDto;
import com.webappfoundation.model.AuditEvent;
import com.webappfoundation.repository.AuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin view over the audit_log (the "Logs" section). ADMIN + SUPER_ADMIN (SecurityConfig gates
 * /api/v1/admin/**). Read-only; returns the most recent events, optionally filtered by action or actor.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
public class AdminAuditController {

    private final AuditEventRepository repository;

    public AdminAuditController(AuditEventRepository repository) {
        this.repository = repository;
    }

    /** GET /api/v1/admin/audit?action=&actorEmail=&limit=  — latest events (newest first), max 1000. */
    @GetMapping
    public List<AuditEventDto> list(@RequestParam(required = false) String action,
                                    @RequestParam(required = false) String actorEmail,
                                    @RequestParam(defaultValue = "200") int limit) {
        Pageable page = PageRequest.of(0, Math.min(Math.max(limit, 1), 1000));
        Page<AuditEvent> events;
        if (action != null && !action.isBlank()) {
            events = repository.findByActionOrderByCreatedAtDesc(action.trim(), page);
        } else if (actorEmail != null && !actorEmail.isBlank()) {
            events = repository.findByActorEmailContainingIgnoreCaseOrderByCreatedAtDesc(actorEmail.trim(), page);
        } else {
            events = repository.findAllByOrderByCreatedAtDesc(page);
        }
        return events.map(this::toDto).getContent();
    }

    private AuditEventDto toDto(AuditEvent e) {
        return AuditEventDto.builder()
                .id(e.getId())
                .createdAt(e.getCreatedAt())
                .userId(e.getUserId())
                .actorEmail(e.getActorEmail())
                .actorRole(e.getActorRole())
                .action(e.getAction())
                .outcome(e.getOutcome())
                .targetType(e.getTargetType())
                .targetId(e.getTargetId())
                .ipAddress(e.getIpAddress())
                .requestId(e.getRequestId())
                .detail(e.getDetail())
                .build();
    }
}
