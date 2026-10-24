package com.webappfoundation.service;

import com.webappfoundation.dto.OrderBoardDetailDto;
import com.webappfoundation.dto.OrderBoardDto;
import com.webappfoundation.dto.OrderCardDto;
import com.webappfoundation.dto.OrderDto;
import com.webappfoundation.dto.OrderStageEventDto;
import com.webappfoundation.dto.SprintDto;
import com.webappfoundation.exception.ResourceNotFoundException;
import com.webappfoundation.model.Order;
import com.webappfoundation.model.OrderSprint;
import com.webappfoundation.model.OrderStageEvent;
import com.webappfoundation.model.PaymentState;
import com.webappfoundation.model.User;
import com.webappfoundation.model.WorkflowStage;
import com.webappfoundation.repository.OrderRepository;
import com.webappfoundation.repository.OrderSprintRepository;
import com.webappfoundation.repository.OrderStageEventRepository;
import com.webappfoundation.repository.UserRepository;
import com.webappfoundation.repository.WorkflowRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Assembles the admin order-board read model over the existing order spine — columns (workflow stages in
 * ordinal order + synthetic <b>Blocked</b> + terminal <b>Cancelled</b>), cards, per-column counts + stuck
 * counts, filter-flag counts, and the per-branch split — scoped by {@code (workflowCode, sprintId, branchId?)}.
 * Read-only: moves/mutations stay in {@link OrderService}/{@code WorkflowEngine}. The board fields it exposes
 * live on {@code Order} and are never leaked into the customer {@code OrderDto}.
 */
@Service
public class OrderBoardService {

    private static final String BLOCKED = "BLOCKED";
    private static final String CANCELLED = WorkflowService.CANCELLED_CODE;
    private static final String KIND_STAGE = "STAGE";

    private final OrderRepository orderRepository;
    private final OrderSprintRepository sprintRepository;
    private final OrderStageEventRepository stageEventRepository;
    private final UserRepository userRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowService workflowService;
    private final SprintService sprintService;
    private final OrderService orderService;

    public OrderBoardService(OrderRepository orderRepository, OrderSprintRepository sprintRepository,
                             OrderStageEventRepository stageEventRepository, UserRepository userRepository,
                             WorkflowRepository workflowRepository, WorkflowService workflowService,
                             SprintService sprintService, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.sprintRepository = sprintRepository;
        this.stageEventRepository = stageEventRepository;
        this.userRepository = userRepository;
        this.workflowRepository = workflowRepository;
        this.workflowService = workflowService;
        this.sprintService = sprintService;
        this.orderService = orderService;
    }

    // ── Board read model ──

    public OrderBoardDto board(String workflowCode, Long sprintId, Long branchId) {
        Instant now = Instant.now();
        List<WorkflowStage> stages = workflowService.stages(workflowCode);
        Map<String, WorkflowStage> stageByCode = stages.stream()
                .collect(Collectors.toMap(WorkflowStage::getCode, s -> s, (a, b) -> a));

        // Resolve the sprint (explicit id, else the workflow's active one) and its orders.
        OrderSprint sprint = sprintId != null
                ? sprintRepository.findById(sprintId).orElse(null)
                : sprintService.activeSprint(workflowCode);
        Long resolvedSprintId = sprint == null ? null : sprint.getId();
        List<Order> sprintOrders = resolvedSprintId == null
                ? List.of()
                : orderRepository.findByWorkflowCodeAndSprintId(workflowCode, resolvedSprintId);

        // Per-branch split is always the whole sprint (so the all-branches view shows every outlet).
        List<OrderBoardDto.BranchCount> branches = branchCounts(sprintOrders);
        // Columns/cards/flags are scoped to the applied branch filter (null = all branches).
        List<Order> scoped = branchId == null ? sprintOrders
                : sprintOrders.stream().filter(o -> branchId.equals(o.getBranchId())).toList();

        Map<Integer, String> names = customerNames(scoped);

        // Build the columns: each non-cancelled stage (ordinal order), then synthetic Blocked, then Cancelled.
        List<OrderBoardDto.Column> columns = new ArrayList<>();
        Map<String, OrderBoardDto.Column> stageColumns = new LinkedHashMap<>();
        for (WorkflowStage s : stages) {
            if (CANCELLED.equals(s.getCode())) {
                continue;
            }
            OrderBoardDto.Column col = OrderBoardDto.Column.builder()
                    .kind(KIND_STAGE).stageCode(s.getCode()).label(s.getLabel()).ordinal(s.getOrdinal())
                    .targetMinutes(s.getTargetMinutes()).cards(new ArrayList<>()).build();
            columns.add(col);
            stageColumns.put(s.getCode(), col);
        }
        OrderBoardDto.Column blockedCol = OrderBoardDto.Column.builder()
                .kind(BLOCKED).stageCode(BLOCKED).label("Blocked").ordinal(null).cards(new ArrayList<>()).build();
        OrderBoardDto.Column cancelledCol = OrderBoardDto.Column.builder()
                .kind(CANCELLED).stageCode(CANCELLED).label(label(stageByCode, CANCELLED, "Cancelled"))
                .ordinal(null).cards(new ArrayList<>()).build();
        columns.add(blockedCol);
        columns.add(cancelledCol);

        OrderBoardDto.FlagCounts flags = OrderBoardDto.FlagCounts.builder().build();
        for (Order o : scoped) {
            WorkflowStage stage = stageByCode.get(o.getCurrentStageCode());
            OrderCardDto card = toCard(o, stage, names, now);
            // Placement: blocked is orthogonal to the stage; cancelled sits in its own column.
            OrderBoardDto.Column target;
            if (o.isBlocked()) {
                target = blockedCol;
            } else if (CANCELLED.equals(o.getCurrentStageCode())) {
                target = cancelledCol;
            } else {
                target = stageColumns.getOrDefault(o.getCurrentStageCode(), null);
            }
            if (target == null) {
                continue; // order sits in a stage not in this workflow (shouldn't happen) — don't place it
            }
            target.getCards().add(card);
            if (card.isStuck()) {
                flags.setStuck(flags.getStuck() + 1);
            }
            if (o.isBlocked()) {
                flags.setBlocked(flags.getBlocked() + 1);
            }
            if (o.getPaymentState() == PaymentState.AWAITING_PAYMENT) {
                flags.setUnpaid(flags.getUnpaid() + 1);
            }
            if (o.getAssignedUserId() == null && !CANCELLED.equals(o.getCurrentStageCode())) {
                flags.setUnassigned(flags.getUnassigned() + 1);
            }
        }
        for (OrderBoardDto.Column col : columns) {
            col.setCount(col.getCards().size());
            col.setStuckCount((int) col.getCards().stream().filter(OrderCardDto::isStuck).count());
        }

        com.webappfoundation.model.Workflow workflow = workflowRepository.findByCode(workflowCode).orElse(null);
        return OrderBoardDto.builder()
                .workflowCode(workflowCode)
                .workflowLabel(workflow == null ? workflowCode : workflow.getLabel())
                .staffRole(workflow == null ? null : workflow.getStaffRole())
                .sprint(sprint == null ? null : sprintService.find(resolvedSprintId))
                .branchId(branchId)
                .columns(columns)
                .flags(flags)
                .branches(branches)
                .generatedAt(now)
                .build();
    }

    // ── Detail sheet ──

    public OrderBoardDetailDto detail(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        OrderDto dto = orderService.getAdmin(id); // reuse the customer-shaped order (items, totals, cancellable, refundPreview)
        WorkflowStage stage = workflowService.stages(order.getWorkflowCode()).stream()
                .filter(s -> s.getCode().equals(order.getCurrentStageCode())).findFirst().orElse(null);
        Instant now = Instant.now();
        return OrderBoardDetailDto.builder()
                .order(dto)
                .customerName(customerName(order))
                .stageLabel(stage == null ? order.getCurrentStageCode() : stage.getLabel())
                .assignedUserId(order.getAssignedUserId())
                .assignedStaffName(order.getAssignedStaffName())
                .blocked(order.isBlocked())
                .blockReason(order.getBlockReason())
                .blockNote(order.getBlockNote())
                .blockedAt(order.getBlockedAt())
                .stageEnteredAt(order.getStageEnteredAt())
                .targetMinutes(stage == null ? null : stage.getTargetMinutes())
                .minutesInStage(minutesInStage(order, now))
                .stuck(isStuck(order, stage, now))
                .carriedForward(order.isCarriedForward())
                .sprintId(order.getSprintId())
                .channel(order.getChannel())
                .timeline(history(id))
                .build();
    }

    // ── Stage-history timeline ──

    public List<OrderStageEventDto> history(Long id) {
        Order order = orderRepository.findById(id).orElse(null);
        Map<String, String> labels = order == null ? Map.of()
                : workflowService.stages(order.getWorkflowCode()).stream()
                        .collect(Collectors.toMap(WorkflowStage::getCode, WorkflowStage::getLabel, (a, b) -> a));
        return stageEventRepository.findByOrderIdOrderByEnteredAtAsc(id).stream()
                .map(e -> toEventDto(e, labels)).toList();
    }

    // ── Mapping / compute helpers ──

    private OrderCardDto toCard(Order o, WorkflowStage stage, Map<Integer, String> names, Instant now) {
        return OrderCardDto.builder()
                .id(o.getId())
                .orderNumber(o.getOrderNumber())
                .customerName(names.getOrDefault(o.getUserId(), fallbackName(o)))
                .total(o.getTotal())
                .currency(o.getCurrency())
                .itemCount(o.getItems() == null ? 0 : o.getItems().stream().mapToInt(i -> Math.max(1, i.getQuantity())).sum())
                .fulfillmentType(o.getFulfillmentType())
                .paymentMode(o.getPaymentMode())
                .paymentState(o.getPaymentState())
                .branchId(o.getBranchId())
                .branchName(o.getBranchName())
                .assignedUserId(o.getAssignedUserId())
                .assignedStaffName(o.getAssignedStaffName())
                .blocked(o.isBlocked())
                .blockReason(o.getBlockReason())
                .channel(o.getChannel())
                .stageCode(o.getCurrentStageCode())
                .stageEnteredAt(o.getStageEnteredAt())
                .blockedAt(o.getBlockedAt())
                .targetMinutes(stage == null ? null : stage.getTargetMinutes())
                .minutesInStage(minutesInStage(o, now))
                .stuck(isStuck(o, stage, now))
                .carriedForward(o.isCarriedForward())
                .createdAt(o.getCreatedAt())
                .build();
    }

    private OrderStageEventDto toEventDto(OrderStageEvent e, Map<String, String> labels) {
        return OrderStageEventDto.builder()
                .id(e.getId()).stageCode(e.getStageCode())
                .stageLabel(labels.getOrDefault(e.getStageCode(), e.getStageCode()))
                .enteredAt(e.getEnteredAt()).trigger(e.getTrigger()).byUserId(e.getByUserId())
                .migration(e.isMigration())
                .build();
    }

    /** Minutes the order has spent in its current stage; frozen at the block instant while blocked. */
    private long minutesInStage(Order o, Instant now) {
        if (o.getStageEnteredAt() == null) {
            return 0;
        }
        Instant end = o.isBlocked() && o.getBlockedAt() != null ? o.getBlockedAt() : now;
        long minutes = ChronoUnit.MINUTES.between(o.getStageEnteredAt(), end);
        return Math.max(0, minutes);
    }

    /** "Stuck" = past the stage SLA. Never true while blocked (timer paused) or in a terminal stage. */
    private boolean isStuck(Order o, WorkflowStage stage, Instant now) {
        if (o.isBlocked() || stage == null || stage.isTerminal() || stage.getTargetMinutes() == null) {
            return false;
        }
        return minutesInStage(o, now) > stage.getTargetMinutes();
    }

    private List<OrderBoardDto.BranchCount> branchCounts(List<Order> orders) {
        Map<Long, OrderBoardDto.BranchCount> byBranch = new LinkedHashMap<>();
        for (Order o : orders) {
            OrderBoardDto.BranchCount bc = byBranch.computeIfAbsent(o.getBranchId(), k ->
                    OrderBoardDto.BranchCount.builder()
                            .branchId(o.getBranchId())
                            .branchName(o.getBranchName() == null ? "Unassigned" : o.getBranchName())
                            .count(0).build());
            bc.setCount(bc.getCount() + 1);
        }
        return new ArrayList<>(byBranch.values());
    }

    private Map<Integer, String> customerNames(List<Order> orders) {
        Set<Integer> ids = orders.stream().map(Order::getUserId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> names = new java.util.HashMap<>();
        for (User u : userRepository.findAllById(ids)) {
            String name = ((safe(u.getFirstName()) + " " + safe(u.getLastName())).trim());
            names.put(u.getId(), name.isBlank() ? u.getEmail() : name);
        }
        return names;
    }

    private String customerName(Order o) {
        Map<Integer, String> names = customerNames(List.of(o));
        return names.getOrDefault(o.getUserId(), fallbackName(o));
    }

    /** When the owner isn't resolvable, fall back to the delivery contact snapshot, else a placeholder. */
    private String fallbackName(Order o) {
        if (o.getShippingAddress() != null && o.getShippingAddress().getContactName() != null
                && !o.getShippingAddress().getContactName().isBlank()) {
            return o.getShippingAddress().getContactName();
        }
        return "Customer";
    }

    private static String label(Map<String, WorkflowStage> stageByCode, String code, String fallback) {
        WorkflowStage s = stageByCode.get(code);
        return s == null || s.getLabel() == null ? fallback : s.getLabel();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
