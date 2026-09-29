package com.webappfoundation.service;

import com.webappfoundation.dto.AdminReplyRequest;
import com.webappfoundation.dto.CreateSupportCaseRequest;
import com.webappfoundation.dto.PostMessageRequest;
import com.webappfoundation.dto.SupportCaseDto;
import com.webappfoundation.dto.SupportMessageDto;
import com.webappfoundation.dto.SupportThreadDto;
import com.webappfoundation.event.SupportCustomerMessageEvent;
import com.webappfoundation.event.SupportRepliedEvent;
import com.webappfoundation.model.SupportAuthor;
import com.webappfoundation.model.SupportCase;
import com.webappfoundation.model.SupportMessage;
import com.webappfoundation.model.SupportStatus;
import com.webappfoundation.model.User;
import com.webappfoundation.repository.SupportCaseRepository;
import com.webappfoundation.repository.SupportMessageRepository;
import com.webappfoundation.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Threaded customer support. A customer opens a case (subject + first message), both sides exchange
 * messages, and each STAFF reply publishes a {@link SupportRepliedEvent} so the customer is emailed.
 * User-owned reads/writes are scoped and ownership-checked by {@code Integer userId} (= User.id).
 */
@Service
public class SupportService {

    private final SupportCaseRepository caseRepository;
    private final SupportMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;

    public SupportService(SupportCaseRepository caseRepository, SupportMessageRepository messageRepository,
                          UserRepository userRepository, ApplicationEventPublisher events) {
        this.caseRepository = caseRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.events = events;
    }

    // ── Customer ──────────────────────────────────────────────────────────────

    /** Customer opens a new case with a subject and the first CUSTOMER message. */
    @Transactional
    public SupportCaseDto open(Integer userId, CreateSupportCaseRequest req) {
        String subject = req == null ? null : req.getSubject();
        String message = req == null ? null : req.getMessage();
        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subject is required.");
        }
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is required.");
        }
        Instant now = Instant.now();
        SupportCase saved = caseRepository.save(SupportCase.builder()
                .userId(userId)
                .subject(subject.trim())
                .status(SupportStatus.OPEN)
                .createdAt(now)
                .updatedAt(now)
                .build());
        addMessage(saved.getId(), SupportAuthor.CUSTOMER, message.trim(), now, req.getAttachments());
        // Alert the admin (emailed after commit by EmailNotificationListener).
        events.publishEvent(new SupportCustomerMessageEvent(this, saved.getId(), userId,
                saved.getSubject(), message.trim(), true));
        return toCaseDto(saved);
    }

    /** Customer adds a message to their own case. */
    @Transactional
    public SupportCaseDto reply(Integer userId, Long caseId, PostMessageRequest req) {
        SupportCase c = ownedCase(userId, caseId);
        String body = req == null ? null : req.getBody();
        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is required.");
        }
        Instant now = Instant.now();
        addMessage(c.getId(), SupportAuthor.CUSTOMER, body.trim(), now, req.getAttachments());
        c.setUpdatedAt(now);
        // A customer reply on a resolved/closed case reopens it.
        if (c.getStatus() == SupportStatus.RESOLVED || c.getStatus() == SupportStatus.CLOSED) {
            c.setStatus(SupportStatus.OPEN);
        }
        SupportCase saved = caseRepository.save(c);
        // Alert the admin of the new customer reply (emailed after commit by EmailNotificationListener).
        events.publishEvent(new SupportCustomerMessageEvent(this, saved.getId(), userId,
                saved.getSubject(), body.trim(), false));
        return toCaseDto(saved);
    }

    /** The current customer's cases, most-recently-active first. */
    public List<SupportCaseDto> myCases(Integer userId) {
        return caseRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(this::toCaseDto).toList();
    }

    /** The current customer's own case thread (ownership-checked). */
    public SupportThreadDto thread(Integer userId, Long caseId) {
        return toThreadDto(ownedCase(userId, caseId));
    }

    // ── Admin helpdesk ──────────────────────────────────────────────────────────

    /** All cases, most-recently-active first. */
    public List<SupportCaseDto> all() {
        return caseRepository.findAllByOrderByUpdatedAtDesc().stream().map(this::toCaseDto).toList();
    }

    /** Any case's thread. */
    public SupportThreadDto adminThread(Long caseId) {
        return toThreadDto(findCase(caseId));
    }

    /** Admin adds a STAFF reply (optionally changing status) and notifies the customer by email. */
    @Transactional
    public SupportThreadDto adminReply(Long caseId, AdminReplyRequest req) {
        SupportCase c = findCase(caseId);
        String body = req == null ? null : req.getBody();
        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply body is required.");
        }
        Instant now = Instant.now();
        addMessage(c.getId(), SupportAuthor.STAFF, body.trim(), now, req.getAttachments());
        if (req.getStatus() != null) {
            c.setStatus(req.getStatus());
        } else if (c.getStatus() == SupportStatus.OPEN) {
            c.setStatus(SupportStatus.IN_PROGRESS);
        }
        c.setUpdatedAt(now);
        SupportCase saved = caseRepository.save(c);
        // Emailed after commit by EmailNotificationListener (EmailService never throws).
        events.publishEvent(new SupportRepliedEvent(this, saved.getId(), saved.getUserId(),
                saved.getSubject(), body.trim(), saved.getStatus()));
        return toThreadDto(saved);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void addMessage(Long caseId, SupportAuthor author, String body, Instant when, List<String> attachments) {
        messageRepository.save(SupportMessage.builder()
                .caseId(caseId)
                .author(author)
                .body(body)
                .attachments(attachments == null ? new ArrayList<>() : new ArrayList<>(attachments))
                .createdAt(when)
                .build());
    }

    private SupportCase findCase(Long caseId) {
        return caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support case not found: " + caseId));
    }

    private SupportCase ownedCase(Integer userId, Long caseId) {
        SupportCase c = findCase(caseId);
        if (userId == null || !userId.equals(c.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Support case not found: " + caseId);
        }
        return c;
    }

    private SupportCaseDto toCaseDto(SupportCase c) {
        String name = null, email = null;
        if (c.getUserId() != null) {
            User u = userRepository.findById(c.getUserId()).orElse(null);
            if (u != null) {
                name = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                        + (u.getLastName() == null ? "" : u.getLastName())).trim();
                email = u.getEmail();
            }
        }
        return SupportCaseDto.builder()
                .id(c.getId())
                .userId(c.getUserId())
                .customerName(name)
                .customerEmail(email)
                .subject(c.getSubject())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private SupportThreadDto toThreadDto(SupportCase c) {
        List<SupportMessageDto> messages = messageRepository.findByCaseIdOrderByCreatedAtAsc(c.getId())
                .stream()
                .map(m -> SupportMessageDto.builder()
                        .id(m.getId())
                        .caseId(m.getCaseId())
                        .author(m.getAuthor())
                        .body(m.getBody())
                        .attachments(m.getAttachments() == null ? new ArrayList<>() : new ArrayList<>(m.getAttachments()))
                        .createdAt(m.getCreatedAt())
                        .build())
                .toList();
        return SupportThreadDto.builder()
                .supportCase(toCaseDto(c))
                .messages(messages)
                .build();
    }
}
