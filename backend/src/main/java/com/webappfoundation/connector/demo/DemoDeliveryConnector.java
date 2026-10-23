package com.webappfoundation.connector.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webappfoundation.connector.ActionConnector;
import com.webappfoundation.connector.CheckoutContext;
import com.webappfoundation.connector.ConnectorAction;
import com.webappfoundation.connector.ConnectorCategory;
import com.webappfoundation.connector.ConnectorResult;
import com.webappfoundation.connector.OrderStageContext;
import com.webappfoundation.connector.QuoteConnector;
import com.webappfoundation.connector.QuoteOption;
import com.webappfoundation.connector.WebhookConnector;
import com.webappfoundation.model.Order;
import com.webappfoundation.model.TriggerType;
import com.webappfoundation.repository.OrderRepository;
import com.webappfoundation.service.ConnectorConfigService;
import com.webappfoundation.workflow.WorkflowEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The copy-me reference connector, exercising all three SPIs: {@link QuoteConnector} (Standard/Express flat
 * rates at checkout), {@link ActionConnector} ({@code delivery.initiate} when an order is dispatched), and
 * {@link WebhookConnector} (a courier callback that advances the order's stage — idempotent via the engine).
 * A real integration swaps the demo logic for live API calls; the wiring stays identical.
 */
@Component
public class DemoDeliveryConnector implements QuoteConnector, ActionConnector, WebhookConnector {

    public static final String ID = "delivery.demo";
    public static final String DELIVERY_INITIATE = "delivery.initiate";

    private static final Logger log = LoggerFactory.getLogger(DemoDeliveryConnector.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConnectorConfigService configService;
    private final OrderRepository orderRepository;
    private final WorkflowEngine workflowEngine;

    public DemoDeliveryConnector(ConnectorConfigService configService, OrderRepository orderRepository,
                                 WorkflowEngine workflowEngine) {
        this.configService = configService;
        this.orderRepository = orderRepository;
        this.workflowEngine = workflowEngine;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Demo delivery";
    }

    @Override
    public ConnectorCategory category() {
        return ConnectorCategory.SHIPPING;
    }

    @Override
    public boolean enabled() {
        return configService.isEnabled(ID, true);
    }

    @Override
    public Set<ConnectorAction> actions() {
        return Set.of(new ConnectorAction(DELIVERY_INITIATE, "Initiate delivery"));
    }

    // ── QuoteConnector ──
    @Override
    public List<QuoteOption> quote(CheckoutContext ctx) {
        return List.of(
                QuoteOption.builder().connectorId(ID).code("standard").label("Standard delivery")
                        .amount(new BigDecimal("49")).detail("3–5 days").build(),
                QuoteOption.builder().connectorId(ID).code("express").label("Express delivery")
                        .amount(new BigDecimal("99")).detail("1–2 days").build());
    }

    // ── ActionConnector ──
    @Override
    public ConnectorResult execute(String actionCode, OrderStageContext ctx) {
        if (!DELIVERY_INITIATE.equals(actionCode)) {
            return ConnectorResult.fail("Unknown action " + actionCode);
        }
        // A real connector would call the courier API here and store a tracking id.
        return ConnectorResult.ok("Delivery initiated (demo) for order " + ctx.getOrderId());
    }

    // ── WebhookConnector: courier callback advances the order's stage ──
    @Override
    public void handleWebhook(String payload, Map<String, String> headers) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            Long orderId = node.has("orderId") ? node.get("orderId").asLong() : null;
            String stage = node.has("stage") ? node.get("stage").asText() : null;
            if (orderId == null || stage == null) {
                log.warn("delivery.demo webhook missing orderId/stage: {}", payload);
                return;
            }
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null) {
                log.warn("delivery.demo webhook for unknown order {}", orderId);
                return;
            }
            workflowEngine.advance(order, stage, TriggerType.WEBHOOK); // idempotent
        } catch (Exception e) {
            log.warn("delivery.demo webhook parse error: {}", e.getMessage());
        }
    }
}
