package com.webappfoundation.service;

import com.webappfoundation.catalog.CatalogService;
import com.webappfoundation.connector.ActionConnector;
import com.webappfoundation.connector.CheckoutContext;
import com.webappfoundation.connector.ConnectorCategory;
import com.webappfoundation.connector.ConnectorRegistry;
import com.webappfoundation.connector.ConnectorResult;
import com.webappfoundation.connector.OrderItemSnapshot;
import com.webappfoundation.connector.OrderStageContext;
import com.webappfoundation.connector.QuoteConnector;
import com.webappfoundation.connector.QuoteOption;
import com.webappfoundation.dto.BulkUploadResult;
import com.webappfoundation.dto.CartLineInput;
import com.webappfoundation.dto.CatalogItemDto;
import com.webappfoundation.dto.CreatePaymentRequest;
import com.webappfoundation.dto.OrderDto;
import com.webappfoundation.dto.PaymentOrderResponse;
import com.webappfoundation.dto.PlaceOrderRequest;
import com.webappfoundation.dto.QuoteRequest;
import com.webappfoundation.dto.QuoteResponse;
import com.webappfoundation.event.PaymentCapturedEvent;
import com.webappfoundation.exception.ResourceNotFoundException;
import com.webappfoundation.inventory.InventoryService;
import com.webappfoundation.model.Address;
import com.webappfoundation.model.BlockReason;
import com.webappfoundation.model.Branch;
import com.webappfoundation.model.FulfillmentType;
import com.webappfoundation.model.Order;
import com.webappfoundation.model.OrderAdjustment;
import com.webappfoundation.model.OrderItem;
import com.webappfoundation.model.OrderStageEvent;
import com.webappfoundation.model.PaymentMode;
import com.webappfoundation.model.PaymentState;
import com.webappfoundation.model.RefundPolicy;
import com.webappfoundation.model.TriggerType;
import com.webappfoundation.model.User;
import com.webappfoundation.model.UserAddress;
import com.webappfoundation.model.WorkflowStage;
import com.webappfoundation.repository.BranchRepository;
import com.webappfoundation.repository.OrderFeedbackRepository;
import com.webappfoundation.repository.OrderRepository;
import com.webappfoundation.repository.OrderStageEventRepository;
import com.webappfoundation.repository.UserRepository;
import com.webappfoundation.workflow.WorkflowEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The order spine. Places orders (re-priced server-side, inventory reserved, one workflow per order),
 * drives payment reconciliation (capture listener + abandoned-reservation TTL sweep), and handles
 * cancellation with stage-aware refunds. Stage transitions fan out to connector actions via the engine —
 * this service never hardcodes lifecycle steps. All writes are audited (no secrets/PII).
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String CURRENCY = "INR";

    private final OrderRepository orderRepository;
    private final CatalogService catalogService;
    private final InventoryService inventoryService;
    private final WorkflowService workflowService;
    private final SprintService sprintService;
    private final WorkflowEngine workflowEngine;
    private final ConnectorRegistry connectorRegistry;
    private final PaymentService paymentService;
    private final BranchRepository branchRepository;
    private final AddressService addressService;
    private final OrderFeedbackRepository feedbackRepository;
    private final OrderStageEventRepository stageEventRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final SpreadsheetParser spreadsheetParser;

    @Value("${inventory.reservation-ttl-minutes:15}")
    private int reservationTtlMinutes;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    public OrderService(OrderRepository orderRepository, CatalogService catalogService,
                        InventoryService inventoryService, WorkflowService workflowService,
                        SprintService sprintService, WorkflowEngine workflowEngine,
                        ConnectorRegistry connectorRegistry,
                        PaymentService paymentService, BranchRepository branchRepository,
                        AddressService addressService, OrderFeedbackRepository feedbackRepository,
                        OrderStageEventRepository stageEventRepository, UserRepository userRepository,
                        EmailService emailService,
                        AuditService auditService, SpreadsheetParser spreadsheetParser) {
        this.orderRepository = orderRepository;
        this.catalogService = catalogService;
        this.inventoryService = inventoryService;
        this.workflowService = workflowService;
        this.sprintService = sprintService;
        this.workflowEngine = workflowEngine;
        this.connectorRegistry = connectorRegistry;
        this.paymentService = paymentService;
        this.branchRepository = branchRepository;
        this.addressService = addressService;
        this.feedbackRepository = feedbackRepository;
        this.stageEventRepository = stageEventRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.auditService = auditService;
        this.spreadsheetParser = spreadsheetParser;
    }

    // ── Place ──

    @Transactional
    public OrderDto placeOrder(Integer userId, PlaceOrderRequest req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }
        FulfillmentType fulfillment = req.getFulfillmentType() == null ? FulfillmentType.DELIVERY : req.getFulfillmentType();
        PaymentMode paymentMode = req.getPaymentMode() == null ? PaymentMode.ONLINE : req.getPaymentMode();
        if (paymentMode == PaymentMode.PAY_AT_COUNTER && fulfillment != FulfillmentType.SELF_PICKUP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pay at counter is only valid for self-pickup");
        }

        RepricedCart cart = reprice(req.getItems());
        if (cart.workflowCodes.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This cart mixes items with different order workflows — please order them separately");
        }
        String workflowCode = cart.workflowCodes.iterator().next();

        // Resolve the fulfilling branch (validated active) and, for delivery, snapshot the chosen address.
        String branchName = resolveBranchName(req.getBranchId());
        Address shippingAddress = resolveShippingAddress(userId, fulfillment, req.getAddressId());

        // Availability + reservation at the chosen branch (shortfall → 409 rolls back the whole tx).
        if (!inventoryService.availableAll(cart.items, req.getBranchId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An item is not available at the selected branch");
        }
        inventoryService.reserveAll(cart.items, req.getBranchId());

        List<OrderAdjustment> adjustments = selectedAdjustments(userId, cart, fulfillment, req.getConnectorSelections());
        BigDecimal total = cart.subtotal.add(sum(adjustments));

        WorkflowStage initial = workflowService.initialStage(workflowCode);
        Instant now = Instant.now();
        Order order = Order.builder()
                .userId(userId)
                .branchId(req.getBranchId())
                .branchName(branchName)
                .fulfillmentType(fulfillment)
                .paymentMode(paymentMode)
                .workflowCode(workflowCode)
                .currentStageCode(initial.getCode())
                .stageEnteredAt(now)
                .sprintId(sprintService.activeSprintId(workflowCode)) // join the workflow's active sprint (null if none)
                .paymentState(PaymentState.AWAITING_PAYMENT)
                .subtotal(cart.subtotal)
                .total(total)
                .currency(CURRENCY)
                .shippingAddress(shippingAddress)
                .items(cart.items)
                .adjustments(adjustments)
                .createdAt(now)
                .updatedAt(now)
                .build();
        order = orderRepository.save(order);
        order.setOrderNumber(buildOrderNumber(order.getId(), now));
        order.setReferenceId("order_" + order.getId());
        order = orderRepository.save(order);
        // seed the stage-history timeline with the initial stage (no connectors fire for this entry)
        stageEventRepository.save(OrderStageEvent.builder()
                .orderId(order.getId()).stageCode(initial.getCode()).enteredAt(now).trigger(TriggerType.SYSTEM)
                .build());

        PaymentOrderResponse payment = settlePlacement(order, paymentMode, fulfillment, total, workflowCode);

        auditService.record(AuditActions.ORDER_PLACED, AuditActions.SUCCESS, "ORDER", String.valueOf(order.getId()),
                "total=" + total + " workflow=" + workflowCode + " fulfillment=" + fulfillment + " mode=" + paymentMode);
        return toDto(order, payment);
    }

    /** Branch the placement by payment path; returns gateway payment info for an online order (else null). */
    private PaymentOrderResponse settlePlacement(Order order, PaymentMode mode, FulfillmentType fulfillment,
                                                 BigDecimal total, String workflowCode) {
        WorkflowStage onPaid = workflowService.onPaidEntryStage(workflowCode);
        boolean free = total.signum() <= 0;
        boolean payAtCounter = fulfillment == FulfillmentType.SELF_PICKUP && mode == PaymentMode.PAY_AT_COUNTER;

        if (free || payAtCounter) {
            // No online payment: commit the hold and start preparing immediately.
            inventoryService.commitAll(order.getItems(), order.getBranchId());
            if (free) {
                order.setPaymentState(PaymentState.PAID);
                orderRepository.save(order);
            }
            workflowEngine.advance(order, onPaid.getCode(), TriggerType.SYSTEM);
            return null;
        }
        // Online: initial stage has no bindings (nothing fires before payment); open a gateway order.
        return paymentService.createOrder(CreatePaymentRequest.builder()
                .amount(total).currency(CURRENCY).referenceId(order.getReferenceId())
                .build());
    }

    // ── Payment capture → mark paid, commit stock, advance to onPaidEntry (idempotent) ──

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        Order order = orderRepository.findByReferenceId(event.getReferenceId()).orElse(null);
        if (order == null || order.getPaymentState() != PaymentState.AWAITING_PAYMENT) {
            return; // not ours, or already settled — idempotent
        }
        order.setPaymentState(PaymentState.PAID);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        inventoryService.commitAll(order.getItems(), order.getBranchId());
        WorkflowStage onPaid = workflowService.onPaidEntryStage(order.getWorkflowCode());
        workflowEngine.advance(order, onPaid.getCode(), TriggerType.PAYMENT);
        auditService.record(AuditActions.ORDER_PAID, AuditActions.SUCCESS, "ORDER", String.valueOf(order.getId()),
                "captured " + event.getReferenceId());
    }

    // ── Reads ──

    public List<OrderDto> myOrders(Integer userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(o -> toDto(o, null)).toList();
    }

    public OrderDto get(Integer userId, Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        if (!order.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your order");
        }
        return toDto(order, null);
    }

    public List<OrderDto> all() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream().map(o -> toDto(o, null)).toList();
    }

    public OrderDto getAdmin(Long id) {
        return toDto(orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id)), null);
    }

    // ── Admin transitions ──

    @Transactional
    public OrderDto advanceStage(Long id, String stageCode) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        // Payment guard: an unpaid order may not enter a terminal non-cancelled (fulfilled) stage — collect
        // payment first. Mirrors the board's "record the payment" prompt. Cancellation stays allowed.
        WorkflowStage target = workflowService.stage(order.getWorkflowCode(), stageCode);
        if (target.isTerminal() && !WorkflowService.CANCELLED_CODE.equals(stageCode)
                && order.getPaymentState() == PaymentState.AWAITING_PAYMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Collect payment before completing this order");
        }
        workflowEngine.advance(order, stageCode, TriggerType.ADMIN);
        auditService.record(AuditActions.ORDER_STAGE_ADVANCED, AuditActions.SUCCESS, "ORDER", String.valueOf(id),
                "stage=" + stageCode);
        return toDto(order, null);
    }

    // ── Board mutations (optimistic-lock guarded by Order.@Version; stale write → 409) ──

    /** Block an order: keep its stage, freeze the time-in-stage timer. A terminal order cannot be blocked. */
    @Transactional
    public OrderDto block(Long id, BlockReason reason, String note) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        WorkflowStage stage = workflowService.stage(order.getWorkflowCode(), order.getCurrentStageCode());
        if (stage.isTerminal()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A completed/cancelled order cannot be blocked");
        }
        if (order.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is already blocked");
        }
        order.setBlocked(true);
        order.setBlockReason(reason);
        order.setBlockNote(note);
        order.setBlockedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        auditService.record(AuditActions.ORDER_BLOCKED, AuditActions.SUCCESS, "ORDER", String.valueOf(id),
                "reason=" + reason);
        return toDto(order, null);
    }

    /** Resume a blocked order: shift stageEnteredAt forward by the blocked duration so the timer resumes. */
    @Transactional
    public OrderDto resume(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        if (!order.isBlocked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is not blocked");
        }
        Instant now = Instant.now();
        if (order.getBlockedAt() != null && order.getStageEnteredAt() != null) {
            long pausedMs = now.toEpochMilli() - order.getBlockedAt().toEpochMilli();
            if (pausedMs > 0) {
                order.setStageEnteredAt(order.getStageEnteredAt().plusMillis(pausedMs));
            }
        }
        order.setBlocked(false);
        order.setBlockReason(null);
        order.setBlockNote(null);
        order.setBlockedAt(null);
        order.setUpdatedAt(now);
        orderRepository.save(order);
        auditService.record(AuditActions.ORDER_RESUMED, AuditActions.SUCCESS, "ORDER", String.valueOf(id),
                "stage=" + order.getCurrentStageCode());
        return toDto(order, null);
    }

    /** Assign (or, with a null userId, unassign) the order to a staff user; snapshots the name onto the card. */
    @Transactional
    public OrderDto assign(Long id, Integer userId) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        if (userId == null) {
            order.setAssignedUserId(null);
            order.setAssignedStaffName(null);
        } else {
            User staff = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown user"));
            order.setAssignedUserId(staff.getId());
            order.setAssignedStaffName((safe(staff.getFirstName()) + " " + safe(staff.getLastName())).trim());
        }
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        auditService.record(AuditActions.ORDER_ASSIGNED, AuditActions.SUCCESS, "ORDER", String.valueOf(id),
                "assignee=" + (userId == null ? "none" : userId));
        return toDto(order, null);
    }

    /** Email the order's owner a link to complete payment for an unpaid order. */
    @Transactional
    public OrderDto sendPaymentLink(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        if (order.getPaymentState() != PaymentState.AWAITING_PAYMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order is not awaiting payment");
        }
        String email = userRepository.findById(order.getUserId()).map(User::getEmail).orElse(null);
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No email on file for this customer");
        }
        String link = appBaseUrl + "/orders/" + order.getId();
        emailService.sendHtml(email, "Complete your payment — " + safe(order.getOrderNumber()),
                paymentLinkHtml(order, link));
        auditService.record(AuditActions.ORDER_PAYMENT_LINK_SENT, AuditActions.SUCCESS, "ORDER", String.valueOf(id),
                "emailed payment link");
        return toDto(order, null);
    }

    /**
     * Standalone (goodwill) refund that does NOT cancel the order — distinct from {@link #cancel}'s
     * stage-aware refund. Routes through the refund connector and sets {@code REFUND_PENDING}. The order
     * must be PAID; a null amount refunds the full total.
     */
    @Transactional
    public OrderDto refund(Long id, BigDecimal amount, String reason) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        if (order.getPaymentState() != PaymentState.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a paid order can be refunded");
        }
        BigDecimal refund = amount == null ? order.getTotal() : amount;
        if (refund.signum() <= 0 || refund.compareTo(order.getTotal()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund amount must be between 0 and the order total");
        }
        order.setRefundAmount(refund);
        order.setCancelReason(reason); // goodwill note; order stays in its current (non-cancelled) stage
        order.setPaymentState(PaymentState.REFUND_PENDING);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        ConnectorResult result = issueRefundViaConnector(order, refund);
        auditService.record(AuditActions.ORDER_REFUNDED, result.isOk() ? AuditActions.SUCCESS : AuditActions.FAILURE,
                "ORDER", String.valueOf(id), "amount=" + refund + " reason=" + safe(reason)
                        + (result.getDetail() == null ? "" : " — " + result.getDetail()));
        return toDto(order, null);
    }

    /** Find the enabled refund action connector and issue the refund intent for {@code order}. */
    private ConnectorResult issueRefundViaConnector(Order order, BigDecimal amount) {
        ActionConnector refundConnector = connectorRegistry.actionConnectors().stream()
                .filter(c -> c.actions().stream().anyMatch(a -> "refund.issue".equals(a.getCode())))
                .findFirst().orElse(null);
        if (refundConnector == null) {
            log.warn("No refund connector available for order {} — refund marked pending only", order.getId());
            return ConnectorResult.ok("No refund connector; marked REFUND_PENDING");
        }
        OrderStageContext ctx = OrderStageContext.builder()
                .orderId(order.getId()).referenceId(order.getReferenceId()).userId(order.getUserId())
                .stageCode(order.getCurrentStageCode())
                .params(Map.of("refundAmount", amount.toPlainString(), "currency", order.getCurrency()))
                .build();
        try {
            ConnectorResult r = refundConnector.execute("refund.issue", ctx);
            return r == null ? ConnectorResult.ok(null) : r;
        } catch (Exception e) {
            log.warn("Refund connector failed for order {}: {}", order.getId(), e.getMessage());
            return ConnectorResult.fail(e.getMessage());
        }
    }

    private String paymentLinkHtml(Order order, String link) {
        return "<div style='font-family:Arial,sans-serif;max-width:560px;margin:auto;color:#212121'>"
                + "<h2 style='color:#1A3A6D'>Complete your payment</h2>"
                + "<p>Your order <strong>" + safe(order.getOrderNumber()) + "</strong> is awaiting payment of "
                + order.getCurrency() + " " + order.getTotal().toPlainString() + ".</p>"
                + "<p><a href='" + link + "' style='background:#1A3A6D;color:#fff;padding:10px 18px;"
                + "border-radius:6px;text-decoration:none'>Pay now</a></p>"
                + "<p style='color:#888;font-size:12px'>If the button doesn't work, open: " + link + "</p>"
                + "</div>";
    }

    @Transactional
    public OrderDto markPaid(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        order.setPaymentState(PaymentState.PAID);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        auditService.record(AuditActions.ORDER_MARK_PAID, AuditActions.SUCCESS, "ORDER", String.valueOf(id),
                "settled at counter");
        return toDto(order, null);
    }

    // ── Cancel (stage-aware refund) ──

    @Transactional
    public OrderDto cancel(Long orderId, Integer actingUserId, String reason, boolean adminOverride) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!adminOverride && !order.getUserId().equals(actingUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your order");
        }
        WorkflowStage stage = workflowService.stage(order.getWorkflowCode(), order.getCurrentStageCode());
        if (!stage.isCancellable() && !adminOverride) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order can no longer be cancelled");
        }
        BigDecimal refund = computeRefund(stage, order.getTotal());
        order.setRefundAmount(refund);
        order.setCancelReason(reason);
        if (order.getPaymentState() == PaymentState.PAID && refund.signum() > 0) {
            order.setPaymentState(PaymentState.REFUND_PENDING);
        } else if (order.getPaymentState() == PaymentState.AWAITING_PAYMENT) {
            order.setPaymentState(PaymentState.FAILED); // cancelled before payment
        }
        inventoryService.releaseAll(order.getItems(), order.getBranchId());
        // advance to the terminal CANCELLED stage → fans out its bindings (incl. refund.issue when refund > 0)
        workflowEngine.advance(order, WorkflowService.CANCELLED_CODE, adminOverride ? TriggerType.ADMIN : TriggerType.SYSTEM);
        auditService.record(AuditActions.ORDER_CANCELLED, AuditActions.SUCCESS, "ORDER", String.valueOf(orderId),
                "refund=" + refund + " reason=" + safe(reason) + (adminOverride ? " (admin override)" : ""));
        return toDto(order, null);
    }

    // ── Reservation-TTL sweep: reconcile abandoned online payments ──

    @Scheduled(fixedDelayString = "${inventory.reservation-ttl-sweep-ms:60000}")
    @Transactional
    public void sweepAbandonedReservations() {
        Instant cutoff = Instant.now().minus(reservationTtlMinutes, ChronoUnit.MINUTES);
        List<Order> stale = orderRepository.findByPaymentStateAndCreatedAtBefore(PaymentState.AWAITING_PAYMENT, cutoff);
        for (Order order : stale) {
            if (order.getPaymentMode() == PaymentMode.PAY_AT_COUNTER) {
                continue; // intentionally unpaid until collection
            }
            inventoryService.releaseAll(order.getItems(), order.getBranchId());
            order.setPaymentState(PaymentState.FAILED);
            order.setCancelReason("Payment not completed in time");
            workflowEngine.advance(order, WorkflowService.CANCELLED_CODE, TriggerType.SYSTEM);
            auditService.record(AuditActions.ORDER_RESERVATION_EXPIRED, AuditActions.SUCCESS, "ORDER",
                    String.valueOf(order.getId()), "released abandoned reservation after " + reservationTtlMinutes + "m");
            log.info("Swept abandoned order {} (reservation released)", order.getId());
        }
    }

    // ── Checkout quote ──

    public QuoteResponse quote(Integer userId, QuoteRequest req) {
        RepricedCart cart = reprice(req.getItems());
        FulfillmentType fulfillment = req.getFulfillmentType() == null ? FulfillmentType.DELIVERY : req.getFulfillmentType();
        CheckoutContext ctx = checkoutContext(userId, cart, Map.of());
        List<QuoteResponse.Option> options = new ArrayList<>();
        for (QuoteConnector qc : connectorRegistry.quoteConnectors()) {
            if (fulfillment == FulfillmentType.SELF_PICKUP && qc.category() == ConnectorCategory.SHIPPING) {
                continue; // pickup needs no delivery quote
            }
            for (QuoteOption opt : qc.quote(ctx)) {
                options.add(QuoteResponse.Option.builder()
                        .connectorId(opt.getConnectorId()).code(opt.getCode()).label(opt.getLabel())
                        .amount(opt.getAmount()).detail(opt.getDetail()).build());
            }
        }
        return QuoteResponse.builder().subtotal(cart.subtotal).options(options).build();
    }

    // ── Bulk import (compulsory; for historical/offline orders) ──

    @Transactional
    public BulkUploadResult bulkImport(MultipartFile file) {
        BulkUploadResult result = BulkUploadResult.builder().build();
        List<Map<String, String>> rows = spreadsheetParser.parse(file);
        int rowNum = 1;
        for (Map<String, String> row : rows) {
            rowNum++;
            Integer userId = parseInt(row.get("userid"));
            String itemType = row.getOrDefault("itemtype", "PRODUCT").trim();
            Long itemId = parseLong(row.get("itemid"));
            int qty = Math.max(1, parseInt(row.getOrDefault("quantity", "1")) == null ? 1 : parseInt(row.get("quantity")));
            if (userId == null || itemId == null) {
                result.addError(rowNum, "userId and itemId are required");
                continue;
            }
            CatalogItemDto dto = catalogService.resolveOne(itemType, itemId);
            if (dto == null) {
                result.addError(rowNum, "item not found: " + itemType + ":" + itemId);
                continue;
            }
            // Imported as already-paid, at the initial stage, with NO stage actions fired (no emails on import).
            BigDecimal line = dto.getPrice().multiply(BigDecimal.valueOf(qty));
            Instant now = Instant.now();
            String workflowCode = workflowService.resolveWorkflowCode(dto.getWorkflowCode());
            Order order = Order.builder()
                    .userId(userId)
                    .fulfillmentType(FulfillmentType.SELF_PICKUP)
                    .paymentMode(PaymentMode.ONLINE)
                    .workflowCode(workflowCode)
                    .currentStageCode(workflowService.initialStage(workflowCode).getCode())
                    .stageEnteredAt(now)
                    .paymentState(PaymentState.PAID)
                    .subtotal(line).total(line).currency(CURRENCY)
                    .items(new ArrayList<>(List.of(OrderItem.builder()
                            .itemType(itemType).itemId(itemId).name(dto.getName())
                            .unitPrice(dto.getPrice()).quantity(qty).imageUrl(dto.getImageUrl()).build())))
                    .createdAt(now).updatedAt(now)
                    .build();
            order = orderRepository.save(order);
            order.setOrderNumber(buildOrderNumber(order.getId(), now));
            order.setReferenceId("order_" + order.getId());
            orderRepository.save(order);
            stageEventRepository.save(OrderStageEvent.builder()
                    .orderId(order.getId()).stageCode(order.getCurrentStageCode()).enteredAt(now)
                    .trigger(TriggerType.SYSTEM).build());
            result.setCreated(result.getCreated() + 1);
        }
        auditService.record(AuditActions.ORDER_BULK_IMPORT, AuditActions.SUCCESS, "ORDER", null,
                "created=" + result.getCreated() + " failed=" + result.getFailed());
        return result;
    }

    // ── Helpers ──

    /** Re-price lines server-side via the catalog; collects items, subtotal, and the distinct workflow(s). */
    private RepricedCart reprice(List<CartLineInput> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }
        List<OrderItem> items = new ArrayList<>();
        Set<String> workflows = new LinkedHashSet<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartLineInput line : lines) {
            if (line.getQuantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive");
            }
            CatalogItemDto dto = catalogService.resolveOne(line.getItemType(), line.getItemId());
            if (dto == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Item not available: " + line.getItemType() + ":" + line.getItemId());
            }
            // Re-price at the catalog price (base). Variant price overrides are a documented follow-up.
            BigDecimal unit = dto.getPrice();
            items.add(OrderItem.builder()
                    .itemType(line.getItemType()).itemId(line.getItemId())
                    .variantKey(line.getVariantKey()).name(dto.getName())
                    .unitPrice(unit).quantity(line.getQuantity()).imageUrl(dto.getImageUrl())
                    .build());
            subtotal = subtotal.add(unit.multiply(BigDecimal.valueOf(line.getQuantity())));
            workflows.add(workflowService.resolveWorkflowCode(dto.getWorkflowCode()));
        }
        return new RepricedCart(items, subtotal, workflows);
    }

    /** Re-run quote connectors and keep only the client-selected options (as adjustments). */
    private List<OrderAdjustment> selectedAdjustments(Integer userId, RepricedCart cart, FulfillmentType fulfillment,
                                                      Map<String, String> selections) {
        List<OrderAdjustment> adjustments = new ArrayList<>();
        if (selections == null || selections.isEmpty()) {
            return adjustments;
        }
        CheckoutContext ctx = checkoutContext(userId, cart, selections);
        for (QuoteConnector qc : connectorRegistry.quoteConnectors()) {
            if (fulfillment == FulfillmentType.SELF_PICKUP && qc.category() == ConnectorCategory.SHIPPING) {
                continue;
            }
            String selectedCode = selections.get(qc.id());
            if (selectedCode == null) {
                continue;
            }
            for (QuoteOption opt : qc.quote(ctx)) {
                if (selectedCode.equals(opt.getCode())) {
                    adjustments.add(OrderAdjustment.builder()
                            .code(opt.getCode()).label(opt.getLabel()).amount(opt.getAmount())
                            .connectorId(qc.id()).build());
                    break;
                }
            }
        }
        return adjustments;
    }

    /** Validate the chosen branch is active and return its name for the order snapshot (null when unset). */
    private String resolveBranchName(Long branchId) {
        if (branchId == null) {
            return null;
        }
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown branch"));
        if (!branch.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected branch is not available");
        }
        return branch.getName();
    }

    /** For delivery, snapshot the chosen saved address (required); for self-pickup, no address. */
    private Address resolveShippingAddress(Integer userId, FulfillmentType fulfillment, Long addressId) {
        if (fulfillment != FulfillmentType.DELIVERY) {
            return null;
        }
        if (addressId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A delivery address is required");
        }
        UserAddress a = addressService.requireOwned(userId, addressId);
        return Address.builder()
                .contactName(a.getContactName()).phone(a.getPhone())
                .line1(a.getLine1()).line2(a.getLine2()).city(a.getCity()).state(a.getState()).pincode(a.getPincode())
                .latitude(a.getLatitude()).longitude(a.getLongitude())
                .build();
    }

    private CheckoutContext checkoutContext(Integer userId, RepricedCart cart, Map<String, String> selections) {
        List<OrderItemSnapshot> snapshots = cart.items.stream()
                .map(i -> OrderItemSnapshot.builder()
                        .itemType(i.getItemType()).itemId(i.getItemId()).variantKey(i.getVariantKey())
                        .name(i.getName()).unitPrice(i.getUnitPrice()).quantity(i.getQuantity()).build())
                .toList();
        return CheckoutContext.builder()
                .userId(userId).items(snapshots).subtotal(cart.subtotal)
                .shippingAddress(null).selections(selections).build();
    }

    private BigDecimal computeRefund(WorkflowStage stage, BigDecimal total) {
        return switch (stage.getRefundPolicy()) {
            case FULL -> total;
            case PARTIAL -> {
                int pct = stage.getRefundPercent() == null ? 0 : stage.getRefundPercent();
                yield total.multiply(BigDecimal.valueOf(pct)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }
            case NONE -> BigDecimal.ZERO;
        };
    }

    private BigDecimal sum(List<OrderAdjustment> adjustments) {
        return adjustments.stream().map(OrderAdjustment::getAmount)
                .filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String buildOrderNumber(Long id, Instant when) {
        int year = when.atZone(java.time.ZoneOffset.UTC).getYear();
        return String.format("ORD-%d-%06d", year, id);
    }

    private OrderDto toDto(Order o, PaymentOrderResponse payment) {
        WorkflowStage stage = o.getCurrentStageCode() == null ? null
                : workflowService.stages(o.getWorkflowCode()).stream()
                        .filter(s -> s.getCode().equals(o.getCurrentStageCode())).findFirst().orElse(null);
        boolean cancellable = stage != null && stage.isCancellable();
        BigDecimal refundPreview = stage == null ? null : computeRefund(stage, o.getTotal());
        return OrderDto.builder()
                .id(o.getId())
                .orderNumber(o.getOrderNumber())
                .referenceId(o.getReferenceId())
                .branchId(o.getBranchId())
                .branchName(o.getBranchName())
                .fulfillmentType(o.getFulfillmentType())
                .paymentMode(o.getPaymentMode())
                .workflowCode(o.getWorkflowCode())
                .currentStageCode(o.getCurrentStageCode())
                .paymentState(o.getPaymentState())
                .subtotal(o.getSubtotal())
                .total(o.getTotal())
                .refundAmount(o.getRefundAmount())
                .cancelReason(o.getCancelReason())
                .currency(o.getCurrency())
                .shippingAddress(o.getShippingAddress())
                .items(o.getItems().stream().map(this::toItemDto).toList())
                .adjustments(o.getAdjustments().stream().map(this::toAdjustmentDto).toList())
                .cancellable(cancellable)
                .refundPreview(refundPreview)
                .feedbackSubmitted(o.getId() != null && feedbackRepository.existsByOrderId(o.getId()))
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .payment(payment)
                .build();
    }

    private OrderDto.OrderItemDto toItemDto(OrderItem i) {
        return OrderDto.OrderItemDto.builder()
                .itemType(i.getItemType()).itemId(i.getItemId())
                .variantKey(i.getVariantKey()).variantLabel(i.getVariantLabel())
                .name(i.getName()).unitPrice(i.getUnitPrice()).quantity(i.getQuantity()).imageUrl(i.getImageUrl())
                .build();
    }

    private OrderDto.OrderAdjustmentDto toAdjustmentDto(OrderAdjustment a) {
        return OrderDto.OrderAdjustmentDto.builder()
                .code(a.getCode()).label(a.getLabel()).amount(a.getAmount()).connectorId(a.getConnectorId())
                .build();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static Integer parseInt(String v) {
        if (v == null || v.isBlank()) return null;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static Long parseLong(String v) {
        if (v == null || v.isBlank()) return null;
        try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return null; }
    }

    /** Server-side re-pricing result passed between place/quote. */
    private static final class RepricedCart {
        final List<OrderItem> items;
        final BigDecimal subtotal;
        final Set<String> workflowCodes;

        RepricedCart(List<OrderItem> items, BigDecimal subtotal, Set<String> workflowCodes) {
            this.items = items;
            this.subtotal = subtotal;
            this.workflowCodes = workflowCodes;
        }
    }
}
