package com.webappfoundation.service;

import com.webappfoundation.dto.CompleteSprintRequest;
import com.webappfoundation.dto.SprintDto;
import com.webappfoundation.dto.UpsertSprintRequest;
import com.webappfoundation.exception.ResourceNotFoundException;
import com.webappfoundation.model.Order;
import com.webappfoundation.model.OrderSprint;
import com.webappfoundation.model.SprintStatus;
import com.webappfoundation.model.Workflow;
import com.webappfoundation.model.WorkflowStage;
import com.webappfoundation.repository.OrderRepository;
import com.webappfoundation.repository.OrderSprintRepository;
import com.webappfoundation.repository.WorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Owns order sprints — the time-boxed batches the board groups cards by. Invariant: <b>at most one ACTIVE
 * sprint per workflow</b>. Orders join the active sprint at placement ({@code OrderService.placeOrder});
 * completing a sprint closes it, opens the next, and carries forward the ticked open orders (keeping their
 * stage/history/blocked state). Boots each workflow with one active sprint and adopts orphan orders.
 */
@Service
public class SprintService {

    private static final Logger log = LoggerFactory.getLogger(SprintService.class);

    private final OrderSprintRepository sprintRepository;
    private final OrderRepository orderRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowService workflowService;
    private final AuditService auditService;

    public SprintService(OrderSprintRepository sprintRepository, OrderRepository orderRepository,
                         WorkflowRepository workflowRepository, WorkflowService workflowService,
                         AuditService auditService) {
        this.sprintRepository = sprintRepository;
        this.orderRepository = orderRepository;
        this.workflowRepository = workflowRepository;
        this.workflowService = workflowService;
        this.auditService = auditService;
    }

    // ── Reads ──

    public List<SprintDto> list(String workflowCode) {
        return sprintRepository.findByWorkflowCodeOrderByCreatedAtDesc(workflowCode).stream().map(this::toDto).toList();
    }

    /** The workflow's current ACTIVE sprint, or null if none — used by placeOrder + the board default view. */
    public OrderSprint activeSprint(String workflowCode) {
        return sprintRepository.findFirstByWorkflowCodeAndStatusOrderByCreatedAtDesc(workflowCode, SprintStatus.ACTIVE)
                .orElse(null);
    }

    /** Convenience for the order spine: the active sprint's id (null if none) to stamp onto a new order. */
    public Long activeSprintId(String workflowCode) {
        OrderSprint active = activeSprint(workflowCode);
        return active == null ? null : active.getId();
    }

    // ── Create / edit (upsert) ──

    @Transactional
    public SprintDto upsert(UpsertSprintRequest req) {
        if (req.getWorkflowCode() == null || req.getWorkflowCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workflowCode is required");
        }
        if (req.getId() != null) {
            // Edit: name/dates only. Status transitions go through complete(), never here.
            OrderSprint sprint = sprintRepository.findById(req.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + req.getId()));
            if (req.getName() != null && !req.getName().isBlank()) {
                sprint.setName(req.getName());
            }
            sprint.setStartsOn(req.getStartsOn());
            sprint.setEndsOn(req.getEndsOn());
            return toDto(sprintRepository.save(sprint));
        }
        // Create a new ACTIVE sprint — guard the one-active-per-workflow invariant.
        if (activeSprint(req.getWorkflowCode()) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An active sprint already exists for this workflow — complete it before starting a new one");
        }
        OrderSprint created = sprintRepository.save(OrderSprint.builder()
                .workflowCode(req.getWorkflowCode())
                .name(req.getName() == null || req.getName().isBlank() ? "Sprint" : req.getName())
                .startsOn(req.getStartsOn()).endsOn(req.getEndsOn())
                .status(SprintStatus.ACTIVE).createdAt(Instant.now())
                .build());
        return toDto(created);
    }

    // ── Complete (close current, open next, carry forward) ──

    @Transactional
    public SprintDto complete(Long id, CompleteSprintRequest req) {
        OrderSprint current = sprintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + id));
        if (current.getStatus() != SprintStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only the active sprint can be completed");
        }
        current.setStatus(SprintStatus.CLOSED);
        sprintRepository.save(current);

        Instant now = Instant.now();
        OrderSprint next = sprintRepository.save(OrderSprint.builder()
                .workflowCode(current.getWorkflowCode())
                .name(req != null && req.getNextName() != null && !req.getNextName().isBlank()
                        ? req.getNextName() : nextName(current.getName()))
                .startsOn(req == null ? null : req.getNextStartsOn())
                .endsOn(req == null ? null : req.getNextEndsOn())
                .status(SprintStatus.ACTIVE).createdAt(now)
                .build());

        int carried = carryForward(current, next, req == null ? null : req.getCarryForwardOrderIds());
        auditService.record(AuditActions.SPRINT_COMPLETED, AuditActions.SUCCESS, "SPRINT", String.valueOf(id),
                "workflow=" + current.getWorkflowCode() + " next=" + next.getId() + " carriedForward=" + carried);
        return toDto(next);
    }

    /** Re-point the ticked open orders of {@code from} onto {@code to}; skips terminal + foreign orders. */
    private int carryForward(OrderSprint from, OrderSprint to, List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        int carried = 0;
        for (Long orderId : orderIds) {
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null || !from.getId().equals(order.getSprintId())) {
                continue; // not a member of the sprint being completed
            }
            if (isTerminal(order)) {
                continue; // completed/cancelled orders stay with the closed sprint
            }
            order.setSprintId(to.getId());
            order.setCarriedForward(true);
            order.setUpdatedAt(now);
            orderRepository.save(order);
            carried++;
        }
        if (carried > 0) {
            auditService.record(AuditActions.ORDER_CARRIED_FORWARD, AuditActions.SUCCESS, "SPRINT",
                    String.valueOf(from.getId()), "carried " + carried + " order(s) into sprint " + to.getId());
        }
        return carried;
    }

    private boolean isTerminal(Order order) {
        try {
            WorkflowStage stage = workflowService.stage(order.getWorkflowCode(), order.getCurrentStageCode());
            return stage.isTerminal();
        } catch (ResponseStatusException e) {
            return false; // unknown stage — treat as open so it isn't silently stranded
        }
    }

    // ── Bootstrap (§9.4): one ACTIVE sprint per workflow + adopt orphan orders ──

    @EventListener(ApplicationReadyEvent.class)
    @org.springframework.core.annotation.Order(100) // after WorkflowService.seedDefaults (so workflows exist)
    @Transactional
    public void bootstrap() {
        for (Workflow workflow : workflowRepository.findAll()) {
            String code = workflow.getCode();
            OrderSprint active = ensureSingleActive(code);
            adoptOrphans(code, active);
        }
    }

    /** Enforce ≤1 ACTIVE sprint; auto-create a first one if none. Returns the surviving active sprint. */
    private OrderSprint ensureSingleActive(String workflowCode) {
        List<OrderSprint> actives = sprintRepository.findByWorkflowCodeAndStatus(workflowCode, SprintStatus.ACTIVE);
        if (actives.isEmpty()) {
            OrderSprint first = sprintRepository.save(OrderSprint.builder()
                    .workflowCode(workflowCode).name("Sprint 1").startsOn(LocalDate.now())
                    .status(SprintStatus.ACTIVE).createdAt(Instant.now())
                    .build());
            log.info("Sprint bootstrap: created first active sprint for workflow '{}'", workflowCode);
            return first;
        }
        if (actives.size() == 1) {
            return actives.get(0);
        }
        // Invariant repair: keep the newest active, close the rest.
        actives.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        OrderSprint keep = actives.get(0);
        for (OrderSprint extra : actives.subList(1, actives.size())) {
            extra.setStatus(SprintStatus.CLOSED);
            sprintRepository.save(extra);
        }
        log.warn("Sprint bootstrap: workflow '{}' had {} active sprints — kept {}, closed the rest",
                workflowCode, actives.size(), keep.getId());
        return keep;
    }

    /** Adopt orphan (null-sprintId) orders of this workflow into its active sprint. */
    private void adoptOrphans(String workflowCode, OrderSprint active) {
        if (active == null) {
            return;
        }
        List<Order> orphans = orderRepository.findBySprintIdIsNullAndWorkflowCode(workflowCode);
        if (orphans.isEmpty()) {
            return;
        }
        for (Order o : orphans) {
            o.setSprintId(active.getId());
        }
        orderRepository.saveAll(orphans);
        log.info("Sprint bootstrap: adopted {} orphan order(s) into sprint {} ('{}')",
                orphans.size(), active.getId(), workflowCode);
    }

    // ── Helpers ──

    private String nextName(String currentName) {
        // "Sprint 7" → "Sprint 8"; otherwise just suffix.
        if (currentName != null) {
            int sp = currentName.lastIndexOf(' ');
            if (sp > 0 && sp < currentName.length() - 1) {
                try {
                    int n = Integer.parseInt(currentName.substring(sp + 1).trim());
                    return currentName.substring(0, sp + 1) + (n + 1);
                } catch (NumberFormatException ignored) {
                    // fall through
                }
            }
        }
        return (currentName == null ? "Sprint" : currentName) + " (next)";
    }

    private SprintDto toDto(OrderSprint s) {
        return SprintDto.builder()
                .id(s.getId()).workflowCode(s.getWorkflowCode()).name(s.getName())
                .startsOn(s.getStartsOn()).endsOn(s.getEndsOn()).status(s.getStatus()).createdAt(s.getCreatedAt())
                .build();
    }
}
