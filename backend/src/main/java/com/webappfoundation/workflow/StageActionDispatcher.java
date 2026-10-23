package com.webappfoundation.workflow;

import com.webappfoundation.connector.ActionConnector;
import com.webappfoundation.connector.ConnectorRegistry;
import com.webappfoundation.connector.ConnectorResult;
import com.webappfoundation.connector.OrderConnector;
import com.webappfoundation.connector.OrderItemSnapshot;
import com.webappfoundation.connector.OrderStageContext;
import com.webappfoundation.event.OrderStageEnteredEvent;
import com.webappfoundation.model.ExecutionStatus;
import com.webappfoundation.model.Order;
import com.webappfoundation.model.TriggerType;
import com.webappfoundation.model.WorkflowBinding;
import com.webappfoundation.model.WorkflowExecution;
import com.webappfoundation.model.WorkflowStage;
import com.webappfoundation.repository.OrderRepository;
import com.webappfoundation.repository.WorkflowBindingRepository;
import com.webappfoundation.repository.WorkflowExecutionRepository;
import com.webappfoundation.repository.WorkflowStageRepository;
import com.webappfoundation.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fans a stage's enabled {@link WorkflowBinding}s out to their connectors when an order enters the stage.
 * Runs {@code @Async} after the transition commits ({@code AFTER_COMMIT}, fallbackExecution=true), mirroring
 * the email listener so the direction is always domain → connector (no bean cycle).
 *
 * <p><b>Dedupe:</b> a binding already recorded {@code SUCCESS} for this {@code (orderId, stageId, bindingId)}
 * is skipped unless the trigger is an explicit admin {@code RETRY} — so a duplicate event never re-sends an
 * email, re-initiates delivery, or re-issues a refund. Each invocation is <b>isolated</b> (one failure never
 * blocks the others), <b>audited</b> (no secrets), and recorded as a {@link WorkflowExecution} row.
 */
@Component
public class StageActionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(StageActionDispatcher.class);

    private final WorkflowStageRepository stageRepository;
    private final WorkflowBindingRepository bindingRepository;
    private final WorkflowExecutionRepository executionRepository;
    private final ConnectorRegistry connectorRegistry;
    private final OrderRepository orderRepository;
    private final AuditService auditService;

    public StageActionDispatcher(WorkflowStageRepository stageRepository,
                                 WorkflowBindingRepository bindingRepository,
                                 WorkflowExecutionRepository executionRepository,
                                 ConnectorRegistry connectorRegistry,
                                 OrderRepository orderRepository,
                                 AuditService auditService) {
        this.stageRepository = stageRepository;
        this.bindingRepository = bindingRepository;
        this.executionRepository = executionRepository;
        this.connectorRegistry = connectorRegistry;
        this.orderRepository = orderRepository;
        this.auditService = auditService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStageEntered(OrderStageEnteredEvent event) {
        WorkflowStage stage = stageRepository
                .findByWorkflowCodeAndCode(event.getWorkflowCode(), event.getStageCode())
                .orElse(null);
        if (stage == null) {
            log.warn("No stage {}/{} — skipping dispatch", event.getWorkflowCode(), event.getStageCode());
            return;
        }
        List<WorkflowBinding> bindings = bindingRepository.findByStageIdAndEnabledTrueOrderByOrdinal(stage.getId());
        if (bindings.isEmpty()) {
            return; // e.g. an online order's initial stage: nothing fires before payment
        }
        Order order = orderRepository.findById(event.getOrderId()).orElse(null);
        if (order == null) {
            log.warn("Order {} not found — skipping dispatch for stage {}", event.getOrderId(), event.getStageCode());
            return;
        }
        OrderStageContext baseCtx = contextFor(order, stage.getCode());

        for (WorkflowBinding binding : bindings) {
            dispatchOne(order, stage, binding, baseCtx, event.getTrigger());
        }
    }

    /** One binding → one connector action, isolated so a failure never blocks the rest. */
    private void dispatchOne(Order order, WorkflowStage stage, WorkflowBinding binding,
                             OrderStageContext baseCtx, TriggerType trigger) {
        WorkflowExecution existing = executionRepository
                .findByOrderIdAndStageIdAndBindingId(order.getId(), stage.getId(), binding.getId())
                .orElse(null);
        if (existing != null && existing.getStatus() == ExecutionStatus.SUCCESS && trigger != TriggerType.RETRY) {
            log.debug("Binding {} already SUCCESS for order {} stage {} — dedupe skip",
                    binding.getId(), order.getId(), stage.getCode());
            return;
        }

        OrderConnector connector = connectorRegistry.byId(binding.getConnectorId());
        ConnectorResult result;
        if (!(connector instanceof ActionConnector action)) {
            result = ConnectorResult.fail("No action connector '" + binding.getConnectorId() + "'");
        } else if (!connector.enabled()) {
            result = ConnectorResult.fail("Connector '" + binding.getConnectorId() + "' is disabled");
        } else {
            OrderStageContext ctx = withParams(baseCtx, binding.getParams());
            try {
                result = action.execute(binding.getActionCode(), ctx);
                if (result == null) {
                    result = ConnectorResult.ok(null);
                }
            } catch (Exception e) {
                log.warn("Connector {} action {} failed for order {}: {}",
                        binding.getConnectorId(), binding.getActionCode(), order.getId(), e.getMessage());
                result = ConnectorResult.fail(e.getMessage());
            }
        }

        record(existing, order, stage, binding, result);
        auditService.record("ORDER_STAGE_ACTION", result.isOk() ? "SUCCESS" : "FAILURE", "ORDER",
                String.valueOf(order.getId()),
                stage.getCode() + " " + binding.getConnectorId() + ":" + binding.getActionCode()
                        + (result.getDetail() == null ? "" : " — " + result.getDetail()));
    }

    private void record(WorkflowExecution existing, Order order, WorkflowStage stage, WorkflowBinding binding,
                        ConnectorResult result) {
        Instant now = Instant.now();
        WorkflowExecution row = existing != null ? existing : WorkflowExecution.builder()
                .orderId(order.getId())
                .stageId(stage.getId())
                .bindingId(binding.getId())
                .createdAt(now)
                .build();
        row.setConnectorId(binding.getConnectorId());
        row.setActionCode(binding.getActionCode());
        row.setStatus(result.isOk() ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED);
        row.setDetail(result.getDetail());
        row.setUpdatedAt(now);
        executionRepository.save(row);
    }

    private OrderStageContext contextFor(Order order, String stageCode) {
        List<OrderItemSnapshot> items = order.getItems().stream()
                .map(i -> OrderItemSnapshot.builder()
                        .itemType(i.getItemType())
                        .itemId(i.getItemId())
                        .variantKey(i.getVariantKey())
                        .name(i.getName())
                        .unitPrice(i.getUnitPrice())
                        .quantity(i.getQuantity())
                        .build())
                .toList();
        return OrderStageContext.builder()
                .orderId(order.getId())
                .referenceId(order.getReferenceId())
                .userId(order.getUserId())
                .stageCode(stageCode)
                .items(items)
                .shippingAddress(order.getShippingAddress())
                .params(orderParams(order))
                .build();
    }

    /** Order-derived params available to every action (binding params override these). */
    private Map<String, String> orderParams(Order order) {
        Map<String, String> p = new HashMap<>();
        if (order.getOrderNumber() != null) p.put("orderNumber", order.getOrderNumber());
        if (order.getTotal() != null) p.put("total", order.getTotal().toPlainString());
        if (order.getRefundAmount() != null) p.put("refundAmount", order.getRefundAmount().toPlainString());
        if (order.getCurrency() != null) p.put("currency", order.getCurrency());
        return p;
    }

    private OrderStageContext withParams(OrderStageContext base, Map<String, String> bindingParams) {
        Map<String, String> merged = new HashMap<>(base.getParams() == null ? Map.of() : base.getParams());
        if (bindingParams != null) {
            merged.putAll(bindingParams); // binding params win
        }
        return OrderStageContext.builder()
                .orderId(base.getOrderId())
                .referenceId(base.getReferenceId())
                .userId(base.getUserId())
                .stageCode(base.getStageCode())
                .items(base.getItems())
                .shippingAddress(base.getShippingAddress())
                .params(merged)
                .build();
    }
}
